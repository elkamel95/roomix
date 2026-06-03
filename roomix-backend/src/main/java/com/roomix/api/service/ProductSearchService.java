package com.roomix.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomix.api.config.AppProperties;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Recherche de produits réels via ChatGPT Vision + Web Search.
 *
 * Principe :
 *  1. On envoie l'image générée (ou l'URL) à l'API OpenAI Responses
 *     avec le tool "web_search_preview" activé.
 *  2. Le prompt demande à ChatGPT d'identifier visuellement les meubles
 *     présents dans l'image puis de chercher sur IKEA.fr les produits
 *     correspondants avec leurs vraies URLs et images CDN.
 *  3. On parse le JSON retourné → List<Product> avec données réelles.
 *
 * Avantages vs API IKEA directe :
 *  - ChatGPT voit l'image → identifie les bons meubles visuellement
 *  - Web search → vraies URLs produits, vraies images CDN, vrais prix
 *  - Pas de dépendance à la structure interne de l'API IKEA
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductSearchService {

    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String SEARCH_MODEL         = "gpt-4.1";
    private static final Duration TIMEOUT            = Duration.ofSeconds(90);

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper;

    // ── Point d'entrée ─────────────────────────────────────────────────────────

    /**
     * @param generation       génération dont on veut suggérer les produits
     * @param style            style de décoration du projet
     * @param preferredBrands  marques souhaitées (IKEA, CONFORAMA)
     * @param resultImageUrl   URL publique de l'image générée
     * @param imageBytes       bytes de l'image générée (pour envoi base64)
     */
    public List<Product> searchProducts(Generation generation,
                                         DecorationStyle style,
                                         List<ProductBrand> preferredBrands,
                                         String resultImageUrl,
                                         byte[] imageBytes) {

        List<String> brands = resolveBrands(preferredBrands);
        if (brands.isEmpty()) return List.of();

        String apiKey = appProperties.getAi().getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ProductSearchService: OPENAI_API_KEY non configurée, skip");
            return List.of();
        }

        log.info("Recherche produits via ChatGPT Vision — style={} brands={}", style, brands);

        try {
            String responseText = callChatGptVisionSearch(
                    apiKey, brands, style, resultImageUrl, imageBytes);
            List<Product> products = parseProducts(responseText, generation);
            log.info("Produits trouvés via ChatGPT Vision: {}", products.size());
            return products;
        } catch (Exception e) {
            log.warn("Recherche produits ChatGPT échouée: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Appel OpenAI Responses API (Vision + Web Search) ───────────────────────

    private String callChatGptVisionSearch(String apiKey,
                                            List<String> brands,
                                            DecorationStyle style,
                                            String resultImageUrl,
                                            byte[] imageBytes) throws Exception {

        String brandsStr = String.join(" et ", brands.stream()
                .map(b -> "CONFORAMA".equals(b) ? "Conforama.fr" : "IKEA.fr")
                .toList());

        String prompt = buildPrompt(brandsStr, style);

        // Construction du message avec image
        // On préfère base64 (plus fiable en prod) sinon URL publique
        Object imageContent;
        if (imageBytes != null && imageBytes.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            imageContent = Map.of(
                    "type", "input_image",
                    "image_url", "data:image/jpeg;base64," + b64
            );
        } else {
            imageContent = Map.of(
                    "type",      "input_image",
                    "image_url", resultImageUrl
            );
        }

        // Corps de la requête Responses API
        Map<String, Object> body = Map.of(
                "model", SEARCH_MODEL,
                "tools", List.of(Map.of("type", "web_search_preview")),
                "input", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        imageContent,
                                        Map.of("type", "input_text", "text", prompt)
                                )
                        )
                )
        );

        String raw = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build()
                .post()
                .uri(OPENAI_RESPONSES_URL)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        return extractText(raw);
    }

    // ── Prompt ─────────────────────────────────────────────────────────────────

    private String buildPrompt(String brands, DecorationStyle style) {
        return "Tu es un expert en décoration intérieure. Regarde attentivement cette image.\n\n"
             + "ÉTAPE 1 — Identifie visuellement les 4 à 6 principaux meubles et objets déco présents "
             + "(canapé, table, lampe, tapis, chaise, étagère, plante, etc.).\n\n"
             + "ÉTAPE 2 — Pour chacun, cherche sur " + brands + " un produit RÉEL actuellement en vente "
             + "qui ressemble visuellement à ce que tu vois dans l'image.\n\n"
             + "Pour chaque produit, fournis OBLIGATOIREMENT :\n"
             + "• name      : nom complet exact du produit sur le site (ex: \"EKTORP Canapé 3 places\")\n"
             + "• color     : couleur/finition exacte (ex: \"Hakebo beige\")\n"
             + "• price     : prix en euros, nombre décimal (ex: 599.00)\n"
             + "• productUrl: URL complète de la page produit "
             + "(ex: https://www.ikea.com/fr/fr/p/ektorp-s49471272/)\n"
             + "• imageUrl  : URL directe de l'image JPG du produit sur le CDN "
             + "(ex: https://www.ikea.com/fr/fr/images/products/ektorp__1175522_s5.jpg)\n"
             + "• brand     : \"IKEA\" ou \"CONFORAMA\" (majuscules)\n"
             + "• category  : un mot parmi SOFA TABLE LAMP CARPET CHAIR DESK PLANT DECORATION OTHER\n\n"
             + "RÈGLES STRICTES :\n"
             + "- N'invente AUCUNE URL. Seulement des produits que tu as réellement trouvés.\n"
             + "- Si tu ne trouves pas l'imageUrl exacte, mets null.\n"
             + "- Chaque productUrl doit commencer par https://www.ikea.com ou https://www.conforama.fr\n\n"
             + "Réponds UNIQUEMENT avec ce JSON, sans texte avant ni après :\n"
             + "{\"products\":["
             + "{\"name\":\"...\",\"color\":\"...\",\"price\":0.0,"
             + "\"productUrl\":\"https://...\",\"imageUrl\":\"https://...\","
             + "\"brand\":\"IKEA\",\"category\":\"SOFA\"}"
             + "]}";
    }

    // ── Extraction du texte de la réponse OpenAI Responses API ─────────────────

    private String extractText(String raw) throws Exception {
        JsonNode root   = objectMapper.readTree(raw);
        JsonNode output = root.path("output");
        // Cherche le dernier message de type "message"
        for (int i = output.size() - 1; i >= 0; i--) {
            JsonNode item = output.get(i);
            if ("message".equals(item.path("type").asText())) {
                for (JsonNode c : item.path("content")) {
                    if ("output_text".equals(c.path("type").asText())) {
                        String text = c.path("text").asText();
                        log.debug("ChatGPT Vision réponse brute ({}chars): {}...",
                                text.length(), text.substring(0, Math.min(200, text.length())));
                        return text;
                    }
                }
            }
        }
        throw new RuntimeException("Aucun texte dans la réponse OpenAI. Raw: "
                + raw.substring(0, Math.min(300, raw.length())));
    }

    // ── Parsing JSON → List<Product> ──────────────────────────────────────────

    private List<Product> parseProducts(String text, Generation generation) {
        try {
            // Extraire le JSON (peut y avoir du markdown autour)
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                log.warn("Pas de JSON trouvé dans la réponse: {}", text.substring(0, Math.min(300, text.length())));
                return List.of();
            }
            String json = text.substring(start, end + 1);
            JsonNode root  = objectMapper.readTree(json);
            JsonNode items = root.path("products");

            List<Product> list = new ArrayList<>();
            for (JsonNode node : items) {
                try {
                    list.add(parseOne(node, generation));
                } catch (Exception e) {
                    log.debug("Produit ignoré: {}", e.getMessage());
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("Erreur parsing produits: {}", e.getMessage());
            return List.of();
        }
    }

    private Product parseOne(JsonNode node, Generation generation) {
        String name       = node.path("name").asText("Produit").trim();
        String color      = nullIfBlank(node.path("color").asText(null));
        String brandStr   = node.path("brand").asText("OTHER").toUpperCase();
        String categoryStr= node.path("category").asText("OTHER").toUpperCase();
        double price      = node.path("price").asDouble(0);
        String productUrl = nullIfBlank(node.path("productUrl").asText(null));
        String imageUrl   = nullIfBlank(node.path("imageUrl").asText(null));

        // Validation URLs : doivent commencer par https://
        if (productUrl != null && !productUrl.startsWith("https://")) productUrl = null;
        if (imageUrl   != null && !imageUrl.startsWith("https://"))   imageUrl   = null;

        ProductBrand brand;
        try { brand = ProductBrand.valueOf(brandStr); }
        catch (Exception e) { brand = ProductBrand.OTHER; }

        ProductCategory category;
        try { category = ProductCategory.valueOf(categoryStr); }
        catch (Exception e) { category = ProductCategory.OTHER; }

        log.info("Produit: {} | {} | {}€ | img={} | url={}",
                name, brand, price,
                imageUrl   != null ? "✓" : "✗",
                productUrl != null ? "✓" : "✗");

        return Product.builder()
                .generation(generation)
                .name(name)
                .description(color)
                .category(category)
                .brand(brand)
                .price(price > 0 ? BigDecimal.valueOf(price) : null)
                .currency("EUR")
                .productUrl(productUrl)
                .affiliateUrl(productUrl)
                .imageUrl(imageUrl)
                .inStock(true)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s;
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
