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
    private static final String IKEA_SEARCH_URL = "https://sik.search.blue.cdtapps.com/fr/fr/search-result-page";
    private static final String IKEA_BASE_URL   = "https://www.ikea.com";

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
     *   https://sik.search.blue.cdtapps.com/fr/fr/search-result-page?q=canap%C3%A9&size=3
     * Note : le paramètre format=json cause une erreur 400 — ne pas l'inclure.
     */
    private List<Product> searchIkea(String keyword, Generation generation) throws Exception {
        String url = UriComponentsBuilder.fromHttpUrl(IKEA_SEARCH_URL)
                .queryParam("q",    URLEncoder.encode(keyword, StandardCharsets.UTF_8))
                .queryParam("size", MAX_PER_KEYWORD)
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

                // Nom = brand name + type (ex: "FRIHETEN Canapé convertible")
                String brandName = p.path("name").asText("").trim();
                String typeName  = p.path("typeName").asText("").trim();
                String name      = (brandName + " " + typeName).trim();

                // Image : mainImageUrl en priorité, sinon allProductImage[0].url
                String imageUrl = firstNonBlank(
                        p.path("mainImageUrl").asText(""),
                        p.path("contextualImageUrl").asText("")
                );
                if (imageUrl.isBlank()) {
                    JsonNode allImgs = p.path("allProductImage");
                    if (allImgs.isArray() && allImgs.size() > 0) {
                        imageUrl = allImgs.get(0).path("url").asText("");
                    }
                }
                log.debug("IKEA imageUrl='{}'", imageUrl);

                // Lien produit : pipUrl en priorité, sinon construit depuis l'id produit
                String pipUrl = p.path("pipUrl").asText("").trim();
                String productUrl;
                if (pipUrl.startsWith("https://www.ikea.com") && pipUrl.contains("/p/")) {
                    productUrl = pipUrl;  // URL complète valide
                } else {
                    // Fallback : construire l'URL depuis l'id (ex: s39216754)
                    String productId = p.path("id").asText(
                                       p.path("itemNoGlobal").asText("")).trim();
                    productUrl = productId.isBlank()
                            ? ""
                            : IKEA_BASE_URL + "/fr/fr/p/-" + productId + "/";
                }
                log.debug("IKEA pipUrl='{}' → productUrl='{}'", pipUrl, productUrl);

                // Prix : salesPrice.numeral (float) ou wholeNumber fallback
                JsonNode salesPrice = p.path("salesPrice");
                double price = 0;
                if (!salesPrice.isMissingNode()) {
                    price = salesPrice.path("numeral").asDouble(0);
                    if (price == 0) {
                        String whole = salesPrice.path("wholeNumber").asText("0").replace(" ", "");
                        try { price = Double.parseDouble(whole); } catch (Exception ignored) {}
                    }
                }

                // Couleur : champ "colour" ou premier élément de "colors"
                String color = null;
                JsonNode colourNode = p.path("colour");
                if (!colourNode.isMissingNode() && !colourNode.isNull()) {
                    color = colourNode.path("name").asText(null);
                }
                if (color == null) {
                    JsonNode colorsArr = p.path("colors");
                    if (colorsArr.isArray() && colorsArr.size() > 0) {
                        color = colorsArr.get(0).path("name").asText(null);
                    }
                }

                ProductCategory cat = guessCategory(typeName.toLowerCase());

                if (name.isBlank() || imageUrl.isBlank()) {
                    log.debug("IKEA produit ignoré (nom/image vide): name={} img={}", name, imageUrl);
                    continue;
                }

                log.info("IKEA produit trouvé: {} | {} | {}€ | img={}", name, productUrl, price, imageUrl);

                list.add(Product.builder()
                        .generation(generation)
                        .name(name)
                        .description(color)
                        .category(cat)
                        .brand(ProductBrand.IKEA)
                        .price(price > 0 ? BigDecimal.valueOf(price) : null)
                        .currency("EUR")
                        .productUrl(productUrl.isBlank() ? null : productUrl)
                        .affiliateUrl(productUrl.isBlank() ? null : productUrl)
                        .imageUrl(imageUrl.startsWith("https://") ? imageUrl : null)
                        .inStock(true)
                        .build());
            } catch (Exception e) {
                log.debug("Produit IKEA ignoré: {}", e.getMessage());
            }
        }
        return list;
    }

    // ── Conforama ──────────────────────────────────────────────────────────────
    // Conforama ne dispose pas d'API publique JSON (site Next.js SSR uniquement).
    // Les recherches Conforama retournent une liste vide → fallback IKEA uniquement.
    private List<Product> searchConforama(String keyword, Generation generation) {
        log.info("Conforama: pas d'API publique disponible, ignoré pour '{}'", keyword);
        return List.of();
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
