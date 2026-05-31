package com.roomix.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomix.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Intégration OpenAI : analyse visuelle (GPT-4o) + génération d'image (gpt-image-2).
 *
 * Pipeline ChatGPT :
 *   1. analyzeRoomStructured() — GPT-4o vision → JSON structuré de la pièce
 *   2. (prompt building dans AiOrchestrationService — 150-250 mots, 3 sections)
 *   3. generateImageToImage()  — gpt-image-2 /images/edits avec params de rendu complets
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiService {

    private final AppProperties  appProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper   objectMapper;

    // ──────────────────────────────────────────────────────────────────────────
    // 1.  Analyse visuelle structurée — GPT-4o
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Analyse la pièce avec GPT-4o Vision et retourne un JSON structuré contenant
     * l'architecture (murs, fenêtres, portes, sol, plafond), l'angle de caméra,
     * le mobilier existant, les zones vides, l'éclairage et les couleurs dominantes.
     *
     * @param imageUrl   URL publique de l'image (mode S3/Supabase)
     * @param imageBytes Bytes locaux — encodés en base64 si présents (mode dev)
     * @return Map désérialisée du JSON d'analyse, ou map minimale en cas d'échec
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRoomStructured(String imageUrl, byte[] imageBytes) {
        AppProperties.Ai.OpenAi cfg = appProperties.getAi().getOpenai();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            log.warn("OPENAI_API_KEY non configurée — analyse structurée impossible");
            return Map.of("roomType", "living room");
        }

        WebClient client = buildClient(cfg);
        String visionModel = cfg.getVisionModel() != null ? cfg.getVisionModel() : "gpt-4o";

        // ── Construire l'URL ou la data-URI base64 ────────────────────────────
        String imageParam;
        if (imageBytes != null && imageBytes.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            imageParam = "data:image/jpeg;base64," + b64;
            log.debug("OpenAI analyse — image en base64 ({} bytes)", imageBytes.length);
        } else {
            imageParam = imageUrl;
            log.debug("OpenAI analyse — image via URL: {}", imageUrl);
        }

        String analysisPrompt = """
                Analyze this room image and return ONLY a valid JSON object with this exact structure.
                Be concise — each value should be a short phrase or a list of short phrases.

                {
                  "roomType": "",
                  "architecture": {
                    "walls": [],
                    "windows": [],
                    "doors": [],
                    "ceiling": "",
                    "floor": ""
                  },
                  "camera": {
                    "angle": "",
                    "perspective": ""
                  },
                  "existingFurniture": [],
                  "emptyAreas": [],
                  "lighting": "",
                  "dominantColors": []
                }

                Return ONLY the JSON. No markdown, no code blocks, no text before or after.
                """;

        Map<String, Object> body = Map.of(
                "model", visionModel,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", analysisPrompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageParam))
                        ))
                ),
                "max_tokens", 800,
                "response_format", Map.of("type", "json_object")
        );

        try {
            Map response = client.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class).map(err -> {
                                log.error("OpenAI analyse erreur {}: {}", resp.statusCode(), err);
                                return new RuntimeException("OpenAI vision " + resp.statusCode() + ": " + err);
                            }))
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(45));

            List choices = (List) response.get("choices");
            Map  choice  = (Map)  choices.get(0);
            Map  message = (Map)  choice.get("message");
            String content = (String) message.get("content");

            log.info("OpenAI analyse pièce ({} chars): {}", content.length(), content);

            // Désérialiser le JSON retourné par GPT-4o
            try {
                Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
                log.info("OpenAI analyse structurée OK — roomType: {}", parsed.get("roomType"));
                return parsed;
            } catch (Exception parseEx) {
                log.warn("Impossible de parser le JSON d'analyse: {} — contenu brut: {}",
                         parseEx.getMessage(), content);
                return Map.of("roomType", "living room", "rawAnalysis", content);
            }

        } catch (Exception e) {
            log.warn("OpenAI analyzeRoomStructured échoué: {}", e.getMessage());
            return Map.of("roomType", "living room");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.  Génération image-to-image — gpt-image-2 (/images/edits)
    //     Retourne les bytes bruts (JPEG après conversion si nécessaire)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Édite la pièce avec gpt-image-2.
     *
     * @param prompt          Prompt d'édition structuré (150-250 mots)
     * @param imageBytes      Bytes de l'image originale
     * @param size            Taille ('auto', '1024x1024', '1536x1024', '2048x2048', etc.)
     * @param quality         Qualité ('auto', 'low', 'medium', 'high')
     * @param format          Format de sortie ('jpeg', 'png', 'webp') — défaut 'jpeg'
     * @param compression     Compression 0-100 pour jpeg/webp (ignoré pour png)
     * @param background      Fond ('auto', 'opaque') — 'transparent' non supporté sur gpt-image-2
     * @return Bytes JPEG de l'image générée
     */
    @SuppressWarnings("unchecked")
    public byte[] generateImageToImage(String prompt,
                                        byte[] imageBytes,
                                        String size,
                                        String quality,
                                        String format,
                                        Integer compression,
                                        String background) {
        AppProperties.Ai.OpenAi cfg = appProperties.getAi().getOpenai();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY non configurée");
        }

        // Client dédié image (6 min) — le client global 120s couperait gpt-image-2
        WebClient client = buildImageClient(cfg);
        String model = cfg.getImageModel() != null ? cfg.getImageModel() : "gpt-image-2";

        // ── Valeurs effectives avec defaults ─────────────────────────────────
        String effectiveSize        = (size        != null && !size.isBlank())    ? size        : "auto";
        String effectiveQuality     = (quality     != null && !quality.isBlank()) ? quality     : "auto";
        String effectiveFormat      = (format      != null && !format.isBlank())  ? format      : "jpeg";
        String effectiveBackground  = (background  != null && !background.isBlank()) ? background : "auto";
        int    effectiveCompression = (compression != null)                        ? compression : 85;

        // ── Conversion de l'image source en PNG (requis par l'API OpenAI) ────
        byte[] pngBytes = convertToPng(imageBytes);
        log.info("OpenAI {} — image convertie en PNG: {} bytes", model, pngBytes.length);

        ByteArrayResource imageResource = new ByteArrayResource(pngBytes) {
            @Override public String getFilename() { return "room.png"; }
        };

        // ── Construction de la requête multipart ─────────────────────────────
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("model",            model);
        bodyBuilder.part("prompt",           prompt);
        bodyBuilder.part("n",                "1");
        bodyBuilder.part("size",             effectiveSize);
        bodyBuilder.part("quality",          effectiveQuality);
        bodyBuilder.part("output_format",    effectiveFormat);
        bodyBuilder.part("background",       effectiveBackground);
        bodyBuilder.part("image",            imageResource, MediaType.IMAGE_PNG);

        // output_compression : uniquement pour jpeg et webp (pas png)
        if (!"png".equalsIgnoreCase(effectiveFormat)) {
            bodyBuilder.part("output_compression", String.valueOf(effectiveCompression));
        }

        log.info("gpt-image-2 request — size={} quality={} format={} compression={} bg={}",
                 effectiveSize, effectiveQuality, effectiveFormat,
                 "png".equalsIgnoreCase(effectiveFormat) ? "n/a" : effectiveCompression,
                 effectiveBackground);
        log.info("gpt-image-2 prompt ({} mots): {}",
                 prompt.trim().split("\\s+").length, prompt);

        Map<String, Object> response = (Map<String, Object>) client.post()
                .uri("/images/edits")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class).map(errBody -> {
                            log.error("gpt-image-2 erreur {}: {}", resp.statusCode(), errBody);
                            return new RuntimeException("gpt-image-2 " + resp.statusCode() + ": " + errBody);
                        }))
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(360)); // aligné sur le responseTimeout du client image (6 min)

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("gpt-image-2 : réponse vide — aucune image");
        }

        // gpt-image-2 retourne toujours b64_json
        String b64 = (String) data.get(0).get("b64_json");
        String url  = (String) data.get(0).get("url");

        byte[] rawResult;
        if (b64 != null && !b64.isBlank()) {
            rawResult = Base64.getDecoder().decode(b64);
            log.info("gpt-image-2 terminé (b64_json) — {} bytes, format: {}", rawResult.length, effectiveFormat);
        } else if (url != null && !url.isBlank()) {
            log.info("gpt-image-2 terminé (url) — téléchargement: {}", url);
            rawResult = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(Duration.ofSeconds(30));
        } else {
            throw new RuntimeException("gpt-image-2 : ni b64_json ni url — " + data.get(0));
        }

        // ── Conversion finale en JPEG pour le stockage uniforme de l'app ─────
        // Si l'API a retourné du JPEG directement, on l'utilise tel quel.
        // PNG et WebP sont convertis pour compatibilité avec le stockage .jpg existant.
        if ("jpeg".equalsIgnoreCase(effectiveFormat)) {
            log.info("gpt-image-2 — JPEG natif, stockage direct ({} bytes)", rawResult.length);
            return rawResult;
        }
        byte[] jpegResult = convertToJpeg(rawResult);
        log.info("gpt-image-2 — {} converti en JPEG: {} bytes", effectiveFormat, jpegResult.length);
        return jpegResult;
    }

    /**
     * Surcharge sans paramètres de rendu — utilise les valeurs par défaut.
     * Conservée pour rétrocompatibilité.
     */
    public byte[] generateImageToImage(String prompt, byte[] imageBytes) {
        return generateImageToImage(prompt, imageBytes, "auto", "auto", "jpeg", 85, "auto");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Analyse simple (legacy — utilisée par AiOrchestrationService step 3 global)
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRoom(String imageUrl) {
        String apiKey  = appProperties.getAi().getOpenai().getApiKey();
        String baseUrl = appProperties.getAi().getOpenai().getBaseUrl();
        String model   = appProperties.getAi().getOpenai().getVisionModel();
        if (model == null) model = "gpt-4o";

        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("roomType", "living room");
        }

        WebClient client = webClientBuilder.clone()
                .baseUrl(baseUrl != null ? baseUrl : "https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text",
                                        "Describe this room briefly. Return JSON: {\"roomType\":\"...\"}"),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                        ))
                ),
                "max_tokens", 100
        );

        try {
            Map response = client.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(20));

            List choices = (List) response.get("choices");
            String content = (String) ((Map) ((Map) choices.get(0)).get("message")).get("content");
            return Map.of("roomType", "living room", "rawAnalysis", content);
        } catch (Exception e) {
            log.warn("analyzeRoom (legacy) échoué: {}", e.getMessage());
            return Map.of("roomType", "living room");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers privés
    // ──────────────────────────────────────────────────────────────────────────

    /** Client standard — hérite du responseTimeout global (120s). Utilisé pour l'analyse vision. */
    private WebClient buildClient(AppProperties.Ai.OpenAi cfg) {
        String baseUrl = cfg.getBaseUrl() != null ? cfg.getBaseUrl() : "https://api.openai.com/v1";
        return webClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                .build();
    }

    /**
     * Client dédié à la génération d'image — responseTimeout de 5 minutes.
     * gpt-image-2 peut prendre jusqu'à 3 min ; le client global (120s) couperait trop tôt.
     * Buffer 50 MB pour les grandes images base64 (2K/4K).
     */
    private WebClient buildImageClient(AppProperties.Ai.OpenAi cfg) {
        String baseUrl = cfg.getBaseUrl() != null ? cfg.getBaseUrl() : "https://api.openai.com/v1";

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(360)); // 6 minutes

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(50 * 1024 * 1024)) // 50 MB pour images 2K/4K en base64
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                .build();
    }

    /** Convertit des bytes image en PNG ARGB (requis par /images/edits). */
    private byte[] convertToPng(byte[] sourceBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (img == null) throw new RuntimeException("Format image non reconnu par ImageIO");
            BufferedImage rgba = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            rgba.getGraphics().drawImage(img, 0, 0, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgba, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Conversion PNG échouée: " + e.getMessage(), e);
        }
    }

    /**
     * Convertit les bytes (PNG ou WebP) en JPEG pour le stockage uniforme.
     * JPEG ne supporte pas l'alpha — fond blanc appliqué.
     */
    private byte[] convertToJpeg(byte[] sourceBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (img == null) return sourceBytes; // fallback : retourner tel quel
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(img, 0, 0, java.awt.Color.WHITE, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpeg", out);
            return out.toByteArray();
        } catch (IOException e) {
            log.warn("Conversion JPEG échouée — bytes bruts retournés: {}", e.getMessage());
            return sourceBytes;
        }
    }
}
