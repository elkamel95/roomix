package com.roomix.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomix.api.config.AppProperties;
import com.roomix.api.model.dto.ProductWithImage;
import com.roomix.api.model.entity.Generation;
import com.roomix.api.model.entity.Product;
import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.ProductCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Recherche de produits réels via ChatGPT Vision + Web Search.
 *
 * Nouveau pipeline :
 *  1. searchProductsForGeneration() — avant la génération IA
 *     → OpenAI web_search_preview trouve les produits correspondant aux critères
 *     → Télécharge les images fond blanc depuis les CDNs marchands
 *     → Retourne List<ProductWithImage> (image bytes inclus)
 *
 *  2. Ces images sont injectées comme objectRefs dans la génération IA
 *     → L'IA place les VRAIS produits trouvés dans la pièce générée
 *
 *  3. toProduct() — convertit ProductWithImage en entité Product pour la DB
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductSearchService {

    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String SEARCH_MODEL         = "gpt-4.1";
    private static final Duration SEARCH_TIMEOUT     = Duration.ofSeconds(90);
    private static final Duration IMG_TIMEOUT        = Duration.ofSeconds(15);

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper;

    // ──────────────────────────────────────────────────────────────────────────
    // Méthode principale — appelée AVANT la génération IA
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Cherche des produits réels correspondant aux critères, télécharge leurs images
     * (fond blanc depuis CDN marchand) et retourne la liste avec bytes d'image.
     *
     * @param style           style de décoration du projet
     * @param preferredBrands marques souhaitées
     * @param searchItemsJson articles spécifiques JSON [{category, maxBudget, color}]
     * @return liste de produits avec imageBytes prêts à être utilisés comme objectRefs
     */
    public List<ProductWithImage> searchProductsForGeneration(
            DecorationStyle style,
            List<ProductBrand> preferredBrands,
            String searchItemsJson) {

        String apiKey = appProperties.getAi().getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ProductSearchService: OPENAI_API_KEY manquante");
            return List.of();
        }

        List<String> brands = resolveBrands(preferredBrands);
        if (brands.isEmpty()) return List.of();

        String brandsStr = String.join(" et ", brands.stream()
                .map(b -> "CONFORAMA".equals(b) ? "Conforama.fr" : "IKEA.fr")
                .toList());

        log.info("Recherche produits pour génération — style={} brands={}", style, brands);

        try {
            String prompt = buildSearchPrompt(brandsStr, style, searchItemsJson);
            String rawText = callOpenAiWebSearch(apiKey, prompt);
            List<ProductWithImage> products = parseAndDownload(rawText);
            log.info("Produits trouvés et images téléchargées: {}", products.size());
            return products;
        } catch (Exception e) {
            log.warn("Recherche produits échouée: {}", e.getMessage());
            return List.of();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Conversion ProductWithImage → Product (entité JPA pour la DB)
    // ──────────────────────────────────────────────────────────────────────────

    public List<Product> toProducts(List<ProductWithImage> pwList, Generation generation) {
        List<Product> result = new ArrayList<>();
        for (ProductWithImage pw : pwList) {
            result.add(Product.builder()
                    .generation(generation)
                    .name(pw.getName())
                    .description(pw.getColor())
                    .category(pw.getCategory() != null ? pw.getCategory() : ProductCategory.OTHER)
                    .brand(pw.getBrand() != null ? pw.getBrand() : ProductBrand.OTHER)
                    .price(pw.getPrice())
                    .currency("EUR")
                    .productUrl(pw.getProductUrl())
                    .affiliateUrl(pw.getProductUrl())
                    .imageUrl(pw.getImageUrl())
                    .inStock(true)
                    .build());
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Prompt de recherche (texte pur, pas de vision — on n'a pas encore l'image)
    // ──────────────────────────────────────────────────────────────────────────

    private String buildSearchPrompt(String brands, DecorationStyle style, String searchItemsJson) {
        StringBuilder itemsBlock = new StringBuilder();
        if (searchItemsJson != null && !searchItemsJson.isBlank()) {
            try {
                JsonNode items = objectMapper.readTree(searchItemsJson);
                if (items.isArray() && items.size() > 0) {
                    itemsBlock.append("L'utilisateur veut EXACTEMENT ces articles :\n");
                    for (JsonNode item : items) {
                        String cat    = item.path("category").asText("").toLowerCase().replace("_", " ");
                        String budget = item.path("maxBudget").asText("").trim();
                        String color  = item.path("color").asText("").trim();
                        if (!cat.isBlank()) {
                            itemsBlock.append("• ").append(cat);
                            if (!color.isBlank())  itemsBlock.append(", couleur : ").append(color);
                            if (!budget.isBlank()) itemsBlock.append(", budget max : ").append(budget).append("€");
                            itemsBlock.append("\n");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        String styleLabel = style.name().replace("_", " ").toLowerCase();
        String itemsSection = itemsBlock.length() > 0
                ? itemsBlock.toString()
                : "les meubles principaux pour un intérieur style " + styleLabel
                  + " (canapé, table, lampe, tapis)";

        return "Tu es un expert en décoration intérieure et shopping en ligne.\n\n"
             + "Cherche sur " + brands + " les produits suivants :\n"
             + itemsSection + "\n"
             + "RÈGLES STRICTES :\n"
             + "1. Trouve des produits RÉELLEMENT disponibles sur " + brands + "\n"
             + "2. Pour imageUrl : utilise l'URL CDN directe de l'image produit SUR FOND BLANC\n"
             + "   - IKEA : https://www.ikea.com/fr/fr/images/products/...jpg\n"
             + "   - Conforama : URL CDN directe de leur site\n"
             + "3. Choisis l'image où le meuble est seul sur fond blanc (pas de mise en scène)\n"
             + "4. N'invente aucune URL — seulement des produits réels que tu as vus\n\n"
             + "Réponds UNIQUEMENT avec ce JSON (sans texte avant ni après) :\n"
             + "{\"products\":[\n"
             + "  {\n"
             + "    \"name\": \"EKTORP Canapé 3 places\",\n"
             + "    \"color\": \"Hakebo beige\",\n"
             + "    \"price\": 599.00,\n"
             + "    \"brand\": \"IKEA\",\n"
             + "    \"category\": \"SOFA\",\n"
             + "    \"productUrl\": \"https://www.ikea.com/fr/fr/p/ektorp-s49471272/\",\n"
             + "    \"imageUrl\": \"https://www.ikea.com/fr/fr/images/products/ektorp__1175522_s5.jpg\"\n"
             + "  }\n"
             + "]}";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Appel OpenAI Responses API (web_search_preview, texte pur)
    // ──────────────────────────────────────────────────────────────────────────

    private String callOpenAiWebSearch(String apiKey, String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", SEARCH_MODEL,
                "tools", List.of(Map.of("type", "web_search_preview")),
                "input", prompt
        );

        String raw = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build()
                .post().uri(OPENAI_RESPONSES_URL)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(SEARCH_TIMEOUT)
                .block();

        return extractText(raw);
    }

    private String extractText(String raw) throws Exception {
        JsonNode root   = objectMapper.readTree(raw);
        JsonNode output = root.path("output");
        for (int i = output.size() - 1; i >= 0; i--) {
            JsonNode item = output.get(i);
            if ("message".equals(item.path("type").asText())) {
                for (JsonNode c : item.path("content")) {
                    if ("output_text".equals(c.path("type").asText())) {
                        return c.path("text").asText();
                    }
                }
            }
        }
        throw new RuntimeException("Aucun texte dans la réponse OpenAI");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Parse JSON + téléchargement images
    // ──────────────────────────────────────────────────────────────────────────

    private List<ProductWithImage> parseAndDownload(String text) throws Exception {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("Pas de JSON dans la réponse OpenAI");
            return List.of();
        }
        JsonNode root  = objectMapper.readTree(text.substring(start, end + 1));
        JsonNode items = root.path("products");

        List<ProductWithImage> result = new ArrayList<>();
        for (JsonNode node : items) {
            try {
                ProductWithImage pw = parseOne(node);
                if (pw == null) continue;

                // Télécharger l'image produit (fond blanc)
                if (pw.getImageUrl() != null) {
                    byte[] bytes = downloadImage(pw.getImageUrl());
                    if (bytes != null && bytes.length > 0) {
                        pw.setImageBytes(bytes);
                        log.info("Produit: {} | {} | {}€ | image={}bytes",
                                pw.getName(), pw.getBrand(), pw.getPrice(), bytes.length);
                        result.add(pw);
                    } else {
                        log.debug("Image non téléchargeable pour {}", pw.getName());
                        // On garde quand même le produit sans bytes (pas de référence visuelle)
                        result.add(pw);
                    }
                } else {
                    result.add(pw);
                }
            } catch (Exception e) {
                log.debug("Produit ignoré: {}", e.getMessage());
            }
        }
        return result;
    }

    private ProductWithImage parseOne(JsonNode node) {
        String name       = node.path("name").asText("").trim();
        String color      = nullIfBlank(node.path("color").asText(null));
        String brandStr   = node.path("brand").asText("OTHER").toUpperCase();
        String categoryStr= node.path("category").asText("OTHER").toUpperCase();
        double price      = node.path("price").asDouble(0);
        String productUrl = nullIfBlank(node.path("productUrl").asText(null));
        String imageUrl   = nullIfBlank(node.path("imageUrl").asText(null));

        if (name.isBlank()) return null;
        if (productUrl != null && !productUrl.startsWith("https://")) productUrl = null;
        if (imageUrl   != null && !imageUrl.startsWith("https://"))   imageUrl   = null;

        ProductBrand brand;
        try { brand = ProductBrand.valueOf(brandStr); }
        catch (Exception e) { brand = ProductBrand.OTHER; }

        ProductCategory category;
        try { category = ProductCategory.valueOf(categoryStr); }
        catch (Exception e) { category = ProductCategory.OTHER; }

        return ProductWithImage.builder()
                .name(name)
                .color(color)
                .brand(brand)
                .category(category)
                .price(price > 0 ? BigDecimal.valueOf(price) : null)
                .productUrl(productUrl)
                .imageUrl(imageUrl)
                .build();
    }

    private byte[] downloadImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(12_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Roomix/1.0)");
            conn.setRequestProperty("Accept", "image/*,*/*");
            conn.setRequestProperty("Referer", "https://www.ikea.com/");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    return in.readAllBytes();
                }
            }
            log.debug("Image HTTP {} pour {}", conn.getResponseCode(), url);
        } catch (Exception e) {
            log.debug("Erreur téléchargement image {}: {}", url, e.getMessage());
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s.trim())) ? null : s.trim();
    }

    private List<String> resolveBrands(List<ProductBrand> preferredBrands) {
        if (preferredBrands != null && !preferredBrands.isEmpty()) {
            return preferredBrands.stream()
                    .filter(b -> b == ProductBrand.IKEA || b == ProductBrand.CONFORAMA)
                    .map(Enum::name).toList();
        }
        return appProperties.getProductSearch().getBrands().stream()
                .filter(b -> "IKEA".equals(b) || "CONFORAMA".equals(b)).toList();
    }
}
