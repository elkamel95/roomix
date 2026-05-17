package com.homegpt.api.service;

import com.homegpt.api.config.AppProperties;
import com.homegpt.api.model.entity.Generation;
import com.homegpt.api.model.entity.Product;
import com.homegpt.api.model.entity.Project;
import com.homegpt.api.model.enums.AiModel;
import com.homegpt.api.model.enums.DecorationStyle;
import com.homegpt.api.model.enums.ProductBrand;
import com.homegpt.api.model.enums.ProductCategory;
import com.homegpt.api.model.enums.ProjectStatus;
import com.homegpt.api.repository.GenerationRepository;
import com.homegpt.api.repository.ProductRepository;
import com.homegpt.api.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiOrchestrationService {

    private final ProjectRepository projectRepository;
    private final GenerationRepository generationRepository;
    private final ProductRepository productRepository;
    private final ReplicateService replicateService;
    private final OpenAiService openAiService;
    private final StorageService storageService;
    private final AppProperties appProperties;

    @Async
    @Transactional
    public void processProjectAsync(UUID projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return;

        project.setStatus(ProjectStatus.PROCESSING);
        projectRepository.save(project);

        long startTime = Instant.now().toEpochMilli();

        try {
            Map<String, Object> roomAnalysis = openAiService.analyzeRoom(project.getOriginalImageUrl());
            project.setRoomAnalysis(roomAnalysis);
            projectRepository.save(project);

            String prompt = buildPrompt(project.getStyle(), roomAnalysis, project.getBudget());
            String negativePrompt = "blurry, distorted, unrealistic, bad quality, watermark, text";

            String resultImageUrl = replicateService.generateImageToImage(
                    project.getOriginalImageUrl(),
                    prompt,
                    negativePrompt
            );

            int processingTime = (int) (Instant.now().toEpochMilli() - startTime);

            Generation generation = Generation.builder()
                    .project(project)
                    .resultImageUrl(resultImageUrl)
                    .prompt(prompt)
                    .negativePrompt(negativePrompt)
                    .model(AiModel.SDXL)
                    .processingTimeMs(processingTime)
                    .build();
            generationRepository.save(generation);

            List<Product> products = generateProductSuggestions(generation, project.getStyle(), project.getBudget());
            productRepository.saveAll(products);

            project.setStatus(ProjectStatus.DONE);
            projectRepository.save(project);

            log.info("Génération terminée pour projet: {} en {}ms", projectId, processingTime);

        } catch (Exception e) {
            log.error("Erreur génération pour projet: {}", projectId, e);
            project.setStatus(ProjectStatus.FAILED);
            projectRepository.save(project);

            Generation failedGen = Generation.builder()
                    .project(project)
                    .prompt("N/A")
                    .model(AiModel.SDXL)
                    .errorMessage(e.getMessage())
                    .processingTimeMs((int) (Instant.now().toEpochMilli() - startTime))
                    .build();
            generationRepository.save(failedGen);
        }
    }

    private String buildPrompt(DecorationStyle style, Map<String, Object> roomAnalysis, BigDecimal budget) {
        String roomType = roomAnalysis != null ? (String) roomAnalysis.getOrDefault("roomType", "living room") : "living room";

        return switch (style) {
            case SCANDINAVIAN -> String.format(
                    "Scandinavian %s, light wood furniture, white walls, minimalist decor, cozy textiles, " +
                    "indoor plants, natural light, beige and white tones, ultra realistic interior design, 8k",
                    roomType);
            case MODERN_LUXURY -> String.format(
                    "Modern luxury %s, marble surfaces, gold accents, velvet sofa, designer furniture, " +
                    "ambient lighting, high ceilings, sophisticated decor, ultra realistic, 8k",
                    roomType);
            case MINIMALIST -> String.format(
                    "Minimalist %s, clean lines, neutral colors, functional furniture, empty walls, " +
                    "natural materials, serene atmosphere, ultra realistic interior design, 8k",
                    roomType);
            case JAPANESE_ZEN -> String.format(
                    "Japanese zen %s, natural wood, bamboo elements, shoji screens, bonsai, " +
                    "neutral tones, peaceful atmosphere, tatami, ultra realistic, 8k",
                    roomType);
            case ARABIC_MODERN -> String.format(
                    "Modern Arabic %s, geometric patterns, rich colors, ornate details, gold accents, " +
                    "plush cushions, lanterns, mashrabiya, ultra realistic interior, 8k",
                    roomType);
            case GAMER_SETUP -> String.format(
                    "Gaming %s, RGB lighting, multiple monitors, gaming chair, dark walls, " +
                    "LED strips, high-tech desk setup, cyberpunk atmosphere, ultra realistic, 8k",
                    roomType);
            case COZY -> String.format(
                    "Cozy %s, warm lighting, soft textiles, plush sofa, candles, " +
                    "bookshelves, warm wood tones, hygge atmosphere, ultra realistic, 8k",
                    roomType);
            case INDUSTRIAL -> String.format(
                    "Industrial %s, exposed brick, metal elements, raw concrete, Edison bulbs, " +
                    "dark wood, loft style, urban feel, ultra realistic interior design, 8k",
                    roomType);
            case SMART_OFFICE -> String.format(
                    "Smart home office %s, ergonomic desk, clean cables, smart devices, " +
                    "minimal decor, productivity setup, plants, natural light, ultra realistic, 8k",
                    roomType);
            case DEVELOPER_SETUP -> String.format(
                    "Senior developer home setup, multiple monitors, mechanical keyboard, " +
                    "dark theme desk, RGB subtle lighting, tech books, clean cable management, " +
                    "ergonomic chair, ultra realistic, 8k",
                    roomType);
        };
    }

    private List<Product> generateProductSuggestions(Generation generation, DecorationStyle style, BigDecimal budget) {
        return switch (style) {
            case SCANDINAVIAN -> List.of(
                    buildProduct(generation, "Canapé EKTORP 3 places", ProductCategory.SOFA, ProductBrand.IKEA, new BigDecimal("599"), "https://www.ikea.com"),
                    buildProduct(generation, "Table basse HEMNES", ProductCategory.TABLE, ProductBrand.IKEA, new BigDecimal("199"), "https://www.ikea.com"),
                    buildProduct(generation, "Lampadaire HEKTAR", ProductCategory.LAMP, ProductBrand.IKEA, new BigDecimal("79"), "https://www.ikea.com"),
                    buildProduct(generation, "Tapis STOENSE", ProductCategory.CARPET, ProductBrand.IKEA, new BigDecimal("129"), "https://www.ikea.com")
            );
            case GAMER_SETUP -> List.of(
                    buildProduct(generation, "Bureau gaming FREDDE", ProductCategory.DESK, ProductBrand.IKEA, new BigDecimal("299"), "https://www.ikea.com"),
                    buildProduct(generation, "Chaise gaming DXRacer", ProductCategory.CHAIR, ProductBrand.AMAZON, new BigDecimal("399"), "https://www.amazon.fr"),
                    buildProduct(generation, "Bande LED RGB Govee", ProductCategory.LAMP, ProductBrand.AMAZON, new BigDecimal("49"), "https://www.amazon.fr")
            );
            default -> List.of(
                    buildProduct(generation, "Canapé 3 places", ProductCategory.SOFA, ProductBrand.IKEA, new BigDecimal("499"), "https://www.ikea.com"),
                    buildProduct(generation, "Lampe de sol", ProductCategory.LAMP, ProductBrand.ACTION, new BigDecimal("39"), "https://www.action.com"),
                    buildProduct(generation, "Plante d'intérieur", ProductCategory.PLANT, ProductBrand.IKEA, new BigDecimal("29"), "https://www.ikea.com")
            );
        };
    }

    private Product buildProduct(Generation generation, String name, ProductCategory category,
                                  ProductBrand brand, BigDecimal price, String url) {
        return Product.builder()
                .generation(generation)
                .name(name)
                .category(category)
                .brand(brand)
                .price(price)
                .currency("EUR")
                .productUrl(url)
                .affiliateUrl(url)
                .inStock(true)
                .build();
    }
}
