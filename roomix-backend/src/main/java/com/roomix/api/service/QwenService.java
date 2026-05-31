package com.roomix.api.service;

import com.roomix.api.config.AppProperties;
import com.roomix.api.model.enums.DecorationStyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service d'intégration avec Alibaba Cloud Model Studio (DashScope).
 *
 * Endpoints utilisés :
 *   - Vision : POST /services/aigc/multimodal-generation/generation  (qwen-vl-max, synchrone)
 *   - Image  : POST /services/aigc/multimodal-generation/generation  (wan2.7-image-pro, synchrone)
 *
 * Modèle image disponibles sur dashscope-intl :
 *   wan2.7-image-pro  ← actuel (meilleure qualité)
 *   wan2.7-image      ← plus rapide
 *   qwen-image-edit-max / qwen-image-edit-plus / qwen-image-2.0-pro
 *
 * Base URL : https://dashscope-intl.aliyuncs.com/api/v1
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QwenService {

    private final AppProperties appProperties;
    private final WebClient.Builder webClientBuilder;

    // ──────────────────────────────────────────────────────────────────────────
    // 1.  Analyse de la pièce  —  Qwen-VL  (format natif DashScope)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Analyse la pièce avec Qwen-VL.
     *
     * @param imageUrl   URL publique de l'image (mode S3/Supabase)
     * @param imageBytes Bytes de l'image locale — si non null, utilisés en base64 (mode dev)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRoom(String imageUrl, byte[] imageBytes) {
        AppProperties.Ai.Qwen cfg = appProperties.getAi().getQwen();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            log.warn("QWEN_API_KEY non configurée — analyse basique utilisée");
            return Map.of("roomType", "living room");
        }

        WebClient client = buildClient(cfg);

        // Préfère le base64 (mode dev local) à l'URL publique quand les bytes sont dispo
        String imageParam = resolveImageParam(imageUrl, imageBytes);

        /*
         * Format natif DashScope multimodal :
         * input.messages[].content = [ { "image": "<url_or_base64>" }, { "text": "..." } ]
         */
        String prompt = """
                Analyse cette image de pièce et retourne un JSON avec :
                - roomType: type de pièce (living room, bedroom, office, kitchen)
                - objects: liste des objets détectés
                - lighting: ambiance lumineuse (bright, dim, natural, artificial)
                - emptyAreas: zones vides exploitables
                - dominantColors: couleurs dominantes
                Réponds UNIQUEMENT en JSON valide, sans texte autour.
                """;

        Map<String, Object> body = Map.of(
                "model", cfg.getVisionModel(),
                "input", Map.of(
                        "messages", List.of(
                                Map.of("role", "user",
                                       "content", List.of(
                                               Map.of("image", imageParam),
                                               Map.of("text", prompt)
                                       ))
                        )
                ),
                "parameters", Map.of("max_tokens", 500)
        );

        try {
            Map response = client.post()
                    .uri("/services/aigc/multimodal-generation/generation")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            Map output  = (Map) response.get("output");
            List choices = (List) output.get("choices");
            Map choice  = (Map) choices.get(0);
            Map message = (Map) choice.get("message");
            List content = (List) message.get("content");
            Map firstPart = (Map) content.get(0);
            String text = (String) firstPart.get("text");

            log.debug("Analyse Qwen-VL: {}", text);
            return Map.of("roomType", "living room", "rawAnalysis", text);

        } catch (Exception e) {
            log.warn("Analyse Qwen-VL échouée: {}", e.getMessage());
            return Map.of("roomType", "living room");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.  Édition image  —  wan2.7-image-pro  (synchrone, format multimodal)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Édite la pièce avec wan2.7-image-pro.
     *
     * Format identique à qwen-image-edit-max : messages/content multimodal.
     * Le modèle reçoit l'image originale + l'instruction d'édition et retourne
     * l'image modifiée. La sélectivité (meubles seulement) est guidée par le prompt.
     *
     * Endpoint : POST /services/aigc/multimodal-generation/generation  (synchrone)
     * Réponse  : output.choices[0].message.content[].image
     *
     * @param referenceImageUrl URL publique de l'image source (production)
     * @param imageBytes        Bytes locaux — encodés en base64 si présents (dev)
     * @param prompt            Instruction d'édition (meubles/déco seulement)
     * @param negativePrompt    Éléments indésirables
     */
    /**
     * Édite la pièce. Accepte optionnellement des images de référence d'objets.
     *
     * @param objectRefs Liste de maps {"title","imageUrl","imageKey"} — chaque image est
     *                   ajoutée au contenu multimodal et son titre est mentionné dans le prompt.
     */
    @SuppressWarnings("unchecked")
    public String generateImageToImage(String referenceImageUrl,
                                       byte[] imageBytes,
                                       String prompt,
                                       String negativePrompt,
                                       List<Map<String, String>> objectRefs) {
        AppProperties.Ai.Qwen cfg = appProperties.getAi().getQwen();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("QWEN_API_KEY non configurée");
        }

        WebClient client = buildClient(cfg);
        String imageParam = resolveImageParam(referenceImageUrl, imageBytes);

        // ── Construction du contenu multimodal ───────────────────────────────
        // CONTRAINTE wan2.7-image-pro : exactement 1 élément "text" par message.
        // → Tout le texte va dans le prompt principal ; les images s'ajoutent ensuite sans texte.
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("text",  prompt));       // ← seul élément text autorisé
        content.add(Map.of("image", imageParam));   // image principale de la pièce

        // Ajout des images de référence (base64 ou URL selon disponibilité, sans text supplémentaire)
        if (objectRefs != null) {
            for (Map<String, String> ref : objectRefs) {
                // "imageParam" contient soit "data:image/jpeg;base64,..." soit l'URL publique
                String imageParam2 = ref.getOrDefault("imageParam",
                                     ref.getOrDefault("imageUrl", ""));
                String title       = ref.getOrDefault("title", "object");
                if (!imageParam2.isBlank()) {
                    content.add(Map.of("image", imageParam2));
                    log.info("Image de référence '{}' ajoutée au contenu multimodal", title);
                }
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", cfg.getImageModel());
        body.put("input", Map.of(
                "messages", List.of(
                        Map.of("role", "user", "content", content)
                )
        ));

        log.info("wan2.7-image-pro request — model={}", cfg.getImageModel());

        Map response = client.post()
                .uri("/services/aigc/multimodal-generation/generation")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class).map(errorBody -> {
                            log.error("wan2.7-image-pro error {}: {}", resp.statusCode(), errorBody);
                            return new RuntimeException(
                                    "wan2.7-image-pro " + resp.statusCode() + ": " + errorBody);
                        }))
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(120));

        Map  output        = (Map)  response.get("output");
        List choices       = (List) output.get("choices");
        Map  choice        = (Map)  choices.get(0);
        Map  message       = (Map)  choice.get("message");
        List responseContent = (List) message.get("content");

        for (Object part : responseContent) {
            Map partMap = (Map) part;
            if (partMap.containsKey("image")) {
                String url = (String) partMap.get("image");
                log.info("wan2.7-image-pro génération terminée: {}", url);
                return url;
            }
        }

        throw new RuntimeException("wan2.7-image-pro: aucune image dans la réponse — " + response);
    }

    /** Surcharge sans objets de référence (rétrocompatibilité). */
    public String generateImageToImage(String referenceImageUrl,
                                       byte[] imageBytes,
                                       String prompt,
                                       String negativePrompt) {
        return generateImageToImage(referenceImageUrl, imageBytes, prompt, negativePrompt, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3.  Mode CHAIN — analyse + stratégie + prompt optimisé  (Qwen-VL)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Chain-of-Thought : envoie l'image au modèle vision avec une instruction en 3 étapes.
     * <ol>
     *   <li>STEP 1 — Analyse détaillée de la pièce (dimensions, matériaux, angles, mobilier…)</li>
     *   <li>STEP 2 — Stratégie de rénovation selon le style cible</li>
     *   <li>STEP 3 — Génération d'un prompt optimisé entre {@code [IMAGE_PROMPT]} et
     *       {@code [/IMAGE_PROMPT]}</li>
     * </ol>
     *
     * @return Le prompt extrait, ou {@code null} si l'extraction échoue (fallback CREATIVE).
     */
    @SuppressWarnings("unchecked")
    public String analyzeAndBuildPrompt(String imageUrl,
                                        byte[] imageBytes,
                                        DecorationStyle style,
                                        String roomType,
                                        String colorPalette,
                                        String customNote,
                                        List<Map<String, String>> objectRefs) {
        AppProperties.Ai.Qwen cfg = appProperties.getAi().getQwen();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            log.warn("QWEN_API_KEY non configurée — mode CHAIN non disponible");
            return null;
        }

        WebClient client = buildClient(cfg);
        String imageParam = resolveImageParam(imageUrl, imageBytes);

        // ── Préparer les contextes supplémentaires ───────────────────────────
        String styleLabel = style.name().replace("_", " ").toLowerCase();
        String rt         = (roomType != null && !roomType.isBlank()) ? roomType : "living room";

        StringBuilder extraPrefsBlock = new StringBuilder();
        if (colorPalette != null && !colorPalette.isBlank())
            extraPrefsBlock.append("- Color palette preference: ").append(colorPalette).append("\n");
        if (objectRefs != null && !objectRefs.isEmpty()) {
            extraPrefsBlock.append("- Integrate these reference objects into the scene:\n");
            for (Map<String, String> ref : objectRefs)
                extraPrefsBlock.append("  * ").append(ref.getOrDefault("title", "object")).append("\n");
        }
        if (customNote != null && !customNote.isBlank())
            extraPrefsBlock.append("- User request: ").append(customNote.trim()).append("\n");

        String analysisPrompt = """
                You are an expert interior designer, architect, and AI image prompt engineer.
                Your task is to analyze this room image and generate an optimized prompt for an AI image generation model.

                STEP 1 — Detailed room analysis:
                Describe the room type, estimated dimensions (ceiling height, overall size), camera angle and perspective (frontal, diagonal, wide), floor material and color, wall material and color, ceiling material, window positions and sizes, door positions, all existing furniture (type, position, material, color), existing decorations, lighting sources (natural and artificial), and architectural constraints (beams, columns, niches, built-in elements).

                STEP 2 — Renovation strategy for style "%s", room type "%s":
                %s
                Preserve the EXACT room geometry and architecture — walls, windows, doors, ceiling, floor, and room proportions must remain unchanged.
                Define precisely which furniture, decor, lighting, and colors to replace to achieve the target style.

                STEP 3 — Generate a single professional image generation prompt in English:
                Write a detailed paragraph describing the renovated room exactly as it looks after renovation.
                Reference the preserved architectural elements (same walls, same windows, same camera perspective).
                Describe all new furniture, materials, textures, colors, lighting, and atmosphere details.
                The prompt must end with: photorealistic, ultra-detailed, interior design magazine quality, 8K, professional photography.

                CRITICAL: your entire response MUST end with this exact block (no text after it):
                [IMAGE_PROMPT]
                <your optimized generation prompt here>
                [/IMAGE_PROMPT]
                """.formatted(styleLabel, rt, extraPrefsBlock.toString());

        Map<String, Object> body = Map.of(
                "model", cfg.getVisionModel(),
                "input", Map.of(
                        "messages", List.of(
                                Map.of("role", "user",
                                        "content", List.of(
                                                Map.of("image", imageParam),
                                                Map.of("text",  analysisPrompt)
                                        ))
                        )
                ),
                "parameters", Map.of("max_tokens", 2000)
        );

        try {
            Map response = client.post()
                    .uri("/services/aigc/multimodal-generation/generation")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(90));

            Map    output    = (Map)  response.get("output");
            List   choices   = (List) output.get("choices");
            Map    choice    = (Map)  choices.get(0);
            Map    message   = (Map)  choice.get("message");
            List   content   = (List) message.get("content");
            Map    firstPart = (Map)  content.get(0);
            String text      = (String) firstPart.get("text");

            log.info("CHAIN — réponse Qwen-VL ({} chars):\n{}", text.length(), text);

            // ── Extraire le prompt entre les marqueurs ───────────────────────
            int start = text.indexOf("[IMAGE_PROMPT]");
            int end   = text.indexOf("[/IMAGE_PROMPT]");
            if (start >= 0 && end > start) {
                String extracted = text.substring(start + "[IMAGE_PROMPT]".length(), end).trim();
                if (!extracted.isBlank()) {
                    log.info("CHAIN — prompt extrait ({} chars): {}", extracted.length(), extracted);
                    return extracted;
                }
            }

            log.warn("CHAIN — marqueur [IMAGE_PROMPT] introuvable, fallback CREATIVE");
            return null;

        } catch (Exception e) {
            log.warn("CHAIN — analyzeAndBuildPrompt échoué: {} — fallback CREATIVE", e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private WebClient buildClient(AppProperties.Ai.Qwen cfg) {
        // Créer un nouveau builder à chaque fois pour éviter l'accumulation de headers
        // (WebClient.Builder est mutable et partagé — clone() garantit un état propre)
        return webClientBuilder.clone()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Si des bytes locaux sont disponibles, encode en base64 data-URL pour les APIs Qwen/Wanx.
     * Sinon retourne l'URL publique (mode S3/Supabase).
     */
    private String resolveImageParam(String imageUrl, byte[] imageBytes) {
        if (imageBytes != null && imageBytes.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            log.debug("Image transmise à Qwen en base64 ({} bytes)", imageBytes.length);
            return "data:image/jpeg;base64," + b64;
        }
        log.debug("Image transmise à Qwen via URL: {}", imageUrl);
        return imageUrl;
    }
}
