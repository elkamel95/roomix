package com.roomix.api.service;

import com.roomix.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiService {

    private final AppProperties appProperties;
    private final WebClient.Builder webClientBuilder;

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRoom(String imageUrl) {
        String apiKey = appProperties.getAi().getOpenai().getApiKey();
        String baseUrl = appProperties.getAi().getOpenai().getBaseUrl();
        String model = appProperties.getAi().getOpenai().getVisionModel();

        WebClient client = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        String systemPrompt = """
                Analyse cette image de pièce et retourne un JSON avec:
                - roomType: type de pièce (living room, bedroom, office, kitchen)
                - objects: liste des objets détectés
                - lighting: ambiance lumineuse (bright, dim, natural, artificial)
                - emptyAreas: zones vides exploitables
                - dominantColors: couleurs dominantes
                Réponds UNIQUEMENT en JSON valide.
                """;

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", systemPrompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                        ))
                ),
                "max_tokens", 500
        );

        try {
            Map response = client.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            List choices = (List) response.get("choices");
            Map choice = (Map) choices.get(0);
            Map message = (Map) choice.get("message");
            String content = (String) message.get("content");

            log.debug("Analyse pièce: {}", content);
            return Map.of("roomType", "living room", "rawAnalysis", content);

        } catch (Exception e) {
            log.warn("Impossible d'analyser la pièce avec GPT-4V: {}", e.getMessage());
            return Map.of("roomType", "living room");
        }
    }
}
