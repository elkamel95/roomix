package com.roomix.api.service;

import com.roomix.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReplicateService {

    private final AppProperties appProperties;
    private final WebClient.Builder webClientBuilder;

    public String generateImageToImage(String sourceImageUrl, String prompt, String negativePrompt) {
        String apiKey = appProperties.getAi().getReplicate().getApiKey();
        String baseUrl = appProperties.getAi().getReplicate().getBaseUrl();
        String sdxlVersion = appProperties.getAi().getReplicate().getSdxlVersion();

        WebClient client = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Token " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        Map<String, Object> input = Map.of(
                "image", sourceImageUrl,
                "prompt", prompt,
                "negative_prompt", negativePrompt,
                "strength", 0.75,
                "num_inference_steps", 30,
                "guidance_scale", 7.5,
                "width", 1024,
                "height", 1024
        );

        Map<String, Object> body = Map.of(
                "version", sdxlVersion,
                "input", input
        );

        Map response = client.post()
                .uri("/predictions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(30));

        String predictionId = (String) response.get("id");
        log.info("Replicate prediction démarrée: {}", predictionId);

        return pollForResult(client, predictionId);
    }

    private String pollForResult(WebClient client, String predictionId) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Génération interrompue");
            }

            Map response = client.get()
                    .uri("/predictions/" + predictionId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            String status = (String) response.get("status");
            log.debug("Replicate status: {}", status);

            if ("succeeded".equals(status)) {
                Object output = response.get("output");
                if (output instanceof java.util.List<?> list && !list.isEmpty()) {
                    return (String) list.get(0);
                }
                throw new RuntimeException("Output inattendu de Replicate");
            }

            if ("failed".equals(status) || "canceled".equals(status)) {
                String error = (String) response.get("error");
                throw new RuntimeException("Génération Replicate échouée: " + error);
            }
        }

        throw new RuntimeException("Timeout: génération Replicate dépassé 2 minutes");
    }
}
