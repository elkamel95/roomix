package com.roomix.api.service;

import com.roomix.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Intégration Replicate — Flux.1 dev (image-to-image).
 *
 * Endpoint model : POST /v1/models/black-forest-labs/flux-dev/predictions
 * Polling        : GET  /v1/predictions/{id}
 *
 * Paramètre clé : prompt_strength (0.0 = aucun changement, 1.0 = ignore l'original)
 * Pour la décoration intérieure : 0.70–0.80 conserve la structure, change les meubles.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReplicateService {

    private final AppProperties appProperties;
    private final WebClient.Builder webClientBuilder;

    private static final String FLUX_MODEL_PATH = "/models/black-forest-labs/flux-dev/predictions";
    private static final double PROMPT_STRENGTH  = 0.60;   // 0.60 = change déco, préserve structure
    private static final int    STEPS            = 28;     // qualité/vitesse
    private static final double GUIDANCE         = 3.5;    // adhérence au prompt

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Génère une image à partir d'une image source et d'un prompt.
     *
     * @param sourceImageUrl URL publique de l'image source (ou null si imageBytes fournis)
     * @param imageBytes     Bytes bruts de l'image source (utilisés si sourceImageUrl est localhost)
     * @param prompt         Prompt de transformation
     * @param negativePrompt Prompt négatif (injecté via --no)
     */
    public String generateImageToImage(String sourceImageUrl,
                                       byte[] imageBytes,
                                       String prompt,
                                       String negativePrompt) {

        AppProperties.Ai.Replicate cfg = appProperties.getAi().getReplicate();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("REPLICATE_API_KEY non configurée");
        }

        WebClient client = buildClient(cfg);

        // Flux.1 n'a pas de champ negative_prompt natif → on l'injecte dans le prompt
        String fullPrompt = prompt
                + (negativePrompt != null && !negativePrompt.isBlank()
                   ? " --no " + negativePrompt
                   : "");

        // Résolution de l'image : préférer base64 si localhost (Replicate doit accéder publiquement)
        String imageParam;
        if (imageBytes != null && imageBytes.length > 0
                && (sourceImageUrl == null || sourceImageUrl.contains("localhost"))) {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            imageParam = "data:image/jpeg;base64," + b64;
            log.info("Flux.1 — image source en base64 ({} bytes, localhost détecté)", imageBytes.length);
        } else {
            imageParam = sourceImageUrl;
            log.info("Flux.1 — image source URL publique: {}", imageParam);
        }

        Map<String, Object> input = Map.of(
                "prompt",          fullPrompt,
                "image",           imageParam,
                "prompt_strength", PROMPT_STRENGTH,
                "num_inference_steps", STEPS,
                "guidance",        GUIDANCE,
                "output_format",   "jpg",
                "output_quality",  90
        );

        log.info("Flux.1 — démarrage prediction (strength={})", PROMPT_STRENGTH);

        // Appel au modèle (sans version hash — utilise toujours la dernière)
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) client.post()
                .uri(FLUX_MODEL_PATH)
                .bodyValue(Map.of("input", input))
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class).map(body -> {
                            log.error("Flux.1 erreur {}: {}", resp.statusCode(), body);
                            return new RuntimeException("Flux.1 " + resp.statusCode() + ": " + body);
                        }))
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(30));

        String predictionId = (String) response.get("id");
        log.info("Flux.1 prediction créée: {}", predictionId);

        return pollForResult(client, predictionId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Polling
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String pollForResult(WebClient client, String predictionId) {
        int maxAttempts = 90;   // 90 × 3s = 4.5 min max
        for (int i = 0; i < maxAttempts; i++) {
            try { Thread.sleep(3_000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Génération Flux interrompue");
            }

            Map<String, Object> poll = (Map<String, Object>) client.get()
                    .uri("/predictions/" + predictionId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(15));

            String status = (String) poll.get("status");
            log.debug("Flux.1 poll {}/{} — status: {}", i + 1, maxAttempts, status);

            switch (status) {
                case "succeeded" -> {
                    Object output = poll.get("output");
                    if (output instanceof List<?> list && !list.isEmpty()) {
                        String url = (String) list.get(0);
                        log.info("Flux.1 terminé: {}", url);
                        return url;
                    }
                    throw new RuntimeException("Flux.1 : output inattendu — " + output);
                }
                case "failed", "canceled" -> {
                    String error = (String) poll.get("error");
                    throw new RuntimeException("Flux.1 échoué (" + status + "): " + error);
                }
                // "starting", "processing" → on continue
            }
        }
        throw new RuntimeException("Flux.1 : timeout après " + (maxAttempts * 3) + "s");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private WebClient buildClient(AppProperties.Ai.Replicate cfg) {
        return webClientBuilder.clone()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("Authorization", "Token " + cfg.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Prefer", "wait")   // Replicate attend la fin si < 60s
                .build();
    }
}
