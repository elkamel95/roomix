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
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Recherche de produits réels via les APIs publiques des enseignes.
 *
 * ── IKEA France ────────────────────────────────────────────────────────────────
 *   API : https://sik.search.blue.cdtapps.com/fr/fr/search-result-page
 *   Params : q={keyword}&size=3&format=json
 *   Retourne : nom, type, prix, imageUrl CDN, URL produit relatif → absolu
 *
 * ── Conforama France ───────────────────────────────────────────────────────────
 *   API Algolia publique exposée par le site
 *   Retourne : nom, prix, imageUrl CDN, URL produit
 *
 * Si une API échoue → liste vide, fallback sur suggestions statiques.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductSearchService {

    // ── IKEA ───────────────────────────────────────────────────────────────────
    private static final String IKEA_SEARCH_URL  = "https://sik.search.blue.cdtapps.com/fr/fr/search-result-page";
    private static final String IKEA_BASE_URL    = "https://www.ikea.com";
    private static final String IKEA_IMG_BASE    = "https://www.ikea.com/fr/fr/images/products/";

    // ── Conforama ──────────────────────────────────────────────────────────────
    private static final String CONFO_SEARCH_URL = "https://www.conforama.fr/search";
    private static final String CONFO_BASE_URL   = "https://www.conforama.fr";

    private static final int    MAX_PER_KEYWORD  = 2;
    private static final Duration TIMEOUT        = Duration.ofSeconds(10);

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper;

    // ── Point d'entrée ─────────────────────────────────────────────────────────

    public List<Product> searchProducts(Generation generation,
                                         DecorationStyle style,
                                         List<ProductBrand> preferredBrands) {
        List<String> brands = resolveBrands(preferredBrands);
        if (brands.isEmpty()) return List.of();

        List<Product> all = new ArrayList<>();

        for (String brand : brands) {
            List<String> keywords = getStyleKeywords(style);
            log.info("Recherche {} — {} mots-clés", brand, keywords.size());

            for (String keyword : keywords) {
                try {
                    List<Product> found = switch (brand) {
                        case "IKEA"      -> searchIkea(keyword, generation);
                        case "CONFORAMA" -> searchConforama(keyword, generation);
                        default          -> List.of();
                    };
                    all.addAll(found);
                    if (all.size() >= 6) break;
                } catch (Exception e) {
                    log.warn("Recherche {} / '{}' échouée: {}", brand, keyword, e.getMessage());
                }
            }
        }

        log.info("Total produits trouvés: {}", all.size());
        return all;
    }

    // ── IKEA API ───────────────────────────────────────────────────────────────

    /**
     * Appelle l'API publique IKEA France et retourne les produits correspondants.
     * URL exemple :
     *   https://sik.search.blue.cdtapps.com/fr/fr/search-result-page?q=canap%C3%A9&size=3&format=json
     */
    private List<Product> searchIkea(String keyword, Generation generation) throws Exception {
        String url = UriComponentsBuilder.fromHttpUrl(IKEA_SEARCH_URL)
                .queryParam("q",      URLEncoder.encode(keyword, StandardCharsets.UTF_8))
                .queryParam("size",   MAX_PER_KEYWORD)
                .queryParam("format", "json")
                .build(true).toUriString();

        String raw = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; Roomix/1.0)")
                .build()
                .get().uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        return parseIkeaResponse(raw, generation);
    }

    private List<Product> parseIkeaResponse(String raw, Generation generation) throws Exception {
        JsonNode root  = objectMapper.readTree(raw);
        JsonNode items = root.path("searchResultPage")
                             .path("products")
                             .path("main")
                             .path("items");

        List<Product> list = new ArrayList<>();
        for (JsonNode item : items) {
            try {
                JsonNode p = item.path("product");
                if (p.isMissingNode()) continue;

                String name     = p.path("name").asText("") + " " + p.path("typeName").asText("");
                String imageUrl = firstNonBlank(
                        p.path("mainImageUrl").asText(""),
                        p.path("contextualImageUrl").asText("")
                );
                String relUrl   = p.path("url").asText("");
                String productUrl = relUrl.startsWith("http") ? relUrl : IKEA_BASE_URL + relUrl;

                // Prix : wholeNumber + decimals
                JsonNode priceNode = p.path("price");
                double price = 0;
                if (!priceNode.isMissingNode()) {
                    String whole    = priceNode.path("wholeNumber").asText("0");
                    String decimals = priceNode.path("decimals").asText("00");
                    price = Double.parseDouble(whole + "." + decimals);
                }

                // Couleur
                String color = null;
                JsonNode colorsNode = p.path("colors");
                if (colorsNode.isArray() && colorsNode.size() > 0) {
                    color = colorsNode.get(0).path("name").asText(null);
                }

                // Catégorie
                String typeName = p.path("typeName").asText("").toLowerCase();
                ProductCategory cat = guessCategory(typeName);

                if (name.isBlank() || imageUrl.isBlank()) continue;

                list.add(Product.builder()
                        .generation(generation)
                        .name(name.trim())
                        .description(color)
                        .category(cat)
                        .brand(ProductBrand.IKEA)
                        .price(price > 0 ? BigDecimal.valueOf(price) : null)
                        .currency("EUR")
                        .productUrl(productUrl.isBlank() ? null : productUrl)
                        .affiliateUrl(productUrl.isBlank() ? null : productUrl)
                        .imageUrl(imageUrl)
                        .inStock(true)
                        .build());
            } catch (Exception e) {
                log.debug("Produit IKEA ignoré: {}", e.getMessage());
            }
        }
        return list;
    }

    // ── Conforama API ──────────────────────────────────────────────────────────

    /**
     * Conforama expose une API SearchSpring.
     * URL : https://www.conforama.fr/search?q={keyword}&format=json
     *
     * Si cette API n'est pas disponible, on essaie l'endpoint alternatif.
     */
    private List<Product> searchConforama(String keyword, Generation generation) throws Exception {
        String url = UriComponentsBuilder.fromHttpUrl(CONFO_SEARCH_URL)
                .queryParam("q",      URLEncoder.encode(keyword, StandardCharsets.UTF_8))
                .queryParam("limit",  MAX_PER_KEYWORD)
                .build(true).toUriString();

        String raw = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/javascript, */*")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; Roomix/1.0)")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .build()
                .get().uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        return parseConforamaResponse(raw, generation);
    }

    private List<Product> parseConforamaResponse(String raw, Generation generation) throws Exception {
        JsonNode root  = objectMapper.readTree(raw);

        // Conforama peut retourner des structures variées selon leur moteur de recherche
        // On essaie plusieurs chemins courants
        JsonNode items = root.path("results");
        if (items.isMissingNode()) items = root.path("products");
        if (items.isMissingNode()) items = root.path("hits");
        if (items.isMissingNode()) return List.of();

        List<Product> list = new ArrayList<>();
        for (JsonNode item : items) {
            try {
                String name     = firstNonBlank(
                        item.path("name").asText(""),
                        item.path("title").asText(""),
                        item.path("label").asText("")
                );
                String imageUrl = firstNonBlank(
                        item.path("imageUrl").asText(""),
                        item.path("image").asText(""),
                        item.path("thumbnail").asText("")
                );
                String relUrl   = firstNonBlank(
                        item.path("url").asText(""),
                        item.path("link").asText("")
                );
                String productUrl = relUrl.startsWith("http") ? relUrl : CONFO_BASE_URL + relUrl;
                double price = item.path("price").asDouble(
                               item.path("salePrice").asDouble(0));
                String color = item.path("color").asText(
                               item.path("colorLabel").asText(null));

                if (name.isBlank() || imageUrl.isBlank()) continue;

                list.add(Product.builder()
                        .generation(generation)
                        .name(name.trim())
                        .description(color != null && !color.isBlank() ? color : null)
                        .category(guessCategory(name.toLowerCase()))
                        .brand(ProductBrand.CONFORAMA)
                        .price(price > 0 ? BigDecimal.valueOf(price) : null)
                        .currency("EUR")
                        .productUrl(productUrl.isBlank() ? null : productUrl)
                        .affiliateUrl(productUrl.isBlank() ? null : productUrl)
                        .imageUrl(imageUrl.startsWith("http") ? imageUrl : null)
                        .inStock(true)
                        .build());
            } catch (Exception e) {
                log.debug("Produit Conforama ignoré: {}", e.getMessage());
            }
        }
        return list;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private ProductCategory guessCategory(String text) {
        String t = text.toLowerCase();
        if (t.contains("canapé") || t.contains("sofa") || t.contains("couch"))  return ProductCategory.SOFA;
        if (t.contains("table"))                                                  return ProductCategory.TABLE;
        if (t.contains("lampe") || t.contains("lamp") || t.contains("lumi"))    return ProductCategory.LAMP;
        if (t.contains("tapis") || t.contains("carpet") || t.contains("rug"))   return ProductCategory.CARPET;
        if (t.contains("bureau") || t.contains("desk"))                          return ProductCategory.DESK;
        if (t.contains("chaise") || t.contains("fauteuil") || t.contains("chair")) return ProductCategory.CHAIR;
        if (t.contains("plante") || t.contains("plant"))                         return ProductCategory.PLANT;
        if (t.contains("étagère") || t.contains("shelf") || t.contains("rangement")) return ProductCategory.SHELF;
        return ProductCategory.OTHER;
    }

    private List<String> resolveBrands(List<ProductBrand> preferredBrands) {
        if (preferredBrands != null && !preferredBrands.isEmpty()) {
            return preferredBrands.stream()
                    .filter(b -> b == ProductBrand.IKEA || b == ProductBrand.CONFORAMA)
                    .map(Enum::name).toList();
        }
        return List.of("IKEA", "CONFORAMA");
    }

    private List<String> getStyleKeywords(DecorationStyle style) {
        return switch (style) {
            case SCANDINAVIAN    -> List.of("canapé beige", "table basse chêne", "lampadaire");
            case MODERN_LUXURY   -> List.of("canapé velours", "table basse marbre", "lustre");
            case MINIMALIST      -> List.of("canapé gris", "table basse bois", "lampe sol");
            case JAPANESE_ZEN    -> List.of("table basse bois", "coussin sol", "lampe");
            case ARABIC_MODERN   -> List.of("canapé", "tapis", "lanterne");
            case GAMER_SETUP     -> List.of("bureau", "chaise bureau", "lampe bureau");
            case INDUSTRIAL      -> List.of("canapé cuir", "table métal", "lampe industrielle");
            case COZY            -> List.of("canapé moelleux", "coussin", "lampe");
            case BOHEMIAN        -> List.of("canapé", "tapis", "lampe rotin");
            case MID_CENTURY     -> List.of("canapé", "table", "lampe arc");
            case CONTEMPORARY    -> List.of("canapé modulable", "table verre", "lampe design");
            case JAPANDI         -> List.of("canapé lin", "table bois", "vase");
            case SMART_OFFICE    -> List.of("bureau", "chaise ergonomique", "lampe bureau");
            case DEVELOPER_SETUP -> List.of("bureau", "chaise ergonomique", "étagère");
            default              -> List.of("canapé", "table basse", "lampe");
        };
    }
}
