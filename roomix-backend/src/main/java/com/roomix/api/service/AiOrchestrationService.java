package com.roomix.api.service;

import com.roomix.api.config.AppProperties;
import com.roomix.api.model.entity.Generation;
import com.roomix.api.model.entity.Product;
import com.roomix.api.model.entity.Project;
import com.roomix.api.model.enums.AiModel;
import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.PromptMode;
import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.ProductCategory;
import com.roomix.api.model.enums.ProjectStatus;
import com.roomix.api.repository.GenerationRepository;
import com.roomix.api.repository.ProductRepository;
import com.roomix.api.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestre le pipeline IA de façon asynchrone.
 *
 * NOTE : @Async + @Transactional sur la même méthode est une anti-pattern Spring :
 *   - @Transactional enveloppe le call en premier (priorité plus haute)
 *   - @Async soumet la tâche au thread-pool et retourne null immédiatement
 *   - @Transactional croit que la méthode est terminée → commit sur un shell vide
 *   - Le vrai corps s'exécute dans le thread-pool SANS transaction → saves JPA échouent
 *
 * Solution : @Async uniquement + TransactionTemplate pour chaque opération DB.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiOrchestrationService {

    private final ProjectRepository    projectRepository;
    private final GenerationRepository generationRepository;
    private final ProductRepository    productRepository;
    private final ReplicateService     replicateService;
    private final OpenAiService        openAiService;
    private final QwenService          qwenService;
    private final StorageService       storageService;
    private final AppProperties        appProperties;
    private final TransactionTemplate  transactionTemplate;

    @Async
    public void processProjectAsync(UUID projectId) {
        log.info("▶ processProjectAsync démarré pour projet: {}", projectId);

        // ── 0. Charger le projet (dans sa propre transaction) ─────────────────
        Project project = transactionTemplate.execute(tx ->
                projectRepository.findById(projectId).orElse(null));

        if (project == null) {
            log.error("✗ Projet introuvable après afterCommit: {}", projectId);
            return;
        }

        // ── 1. Passage en PROCESSING ──────────────────────────────────────────
        transactionTemplate.execute(tx -> {
            project.setStatus(ProjectStatus.PROCESSING);
            projectRepository.save(project);
            return null;
        });
        log.info("● Projet {} → PROCESSING", projectId);

        long startTime = Instant.now().toEpochMilli();

        try {
            AiModel aiModel = project.getAiModel() != null ? project.getAiModel() : AiModel.QWEN;
            boolean useQwen    = AiModel.QWEN.equals(aiModel);
            boolean useFlux    = AiModel.FLUX.equals(aiModel);
            boolean useChatGpt = AiModel.CHATGPT.equals(aiModel);
            log.info("Modèle IA sélectionné: {}", aiModel);

            // ── 2. Charger les bytes de l'image (mode dev local) ──────────────
            byte[] imageBytes = storageService.getImageBytes(project.getOriginalImageKey());
            log.info("Bytes image chargés: {} bytes", imageBytes != null ? imageBytes.length : 0);

            // ── 3. Analyse visuelle de la pièce ───────────────────────────────
            // Qwen-VL pour l'analyse visuelle (tous modèles)
            Map<String, Object> roomAnalysis = qwenService.analyzeRoom(
                    project.getOriginalImageUrl(), imageBytes);

            log.info("Analyse terminée: {}", roomAnalysis);

            final Map<String, Object> finalAnalysis = roomAnalysis;
            transactionTemplate.execute(tx -> {
                project.setRoomAnalysis(finalAnalysis);
                projectRepository.save(project);
                return null;
            });

            // ── 4. Génération image ───────────────────────────────────────────

            // roomType : utilise la valeur forcée par l'utilisateur, sinon celle détectée par l'IA
            String roomType = project.getRoomType() != null
                    ? project.getRoomType()
                    : (roomAnalysis != null ? (String) roomAnalysis.getOrDefault("roomType", "living room") : "living room");

            PromptMode promptMode = project.getPromptMode() != null ? project.getPromptMode() : PromptMode.CREATIVE;

            // ── Résolution des objets de référence (commun à tous les modes) ──
            List<Map<String, String>> resolvedRefsForGeneration = resolveObjectRefs(project.getObjectRefs());

            String prompt;
            if (PromptMode.CHAIN.equals(promptMode)) {
                // ── Mode CHAIN : analyse IA → stratégie → prompt optimisé ────
                log.info("Mode CHAIN activé — lancement de l'analyse Qwen-VL...");
                String chainPrompt = qwenService.analyzeAndBuildPrompt(
                        project.getOriginalImageUrl(), imageBytes,
                        project.getStyle(), roomType,
                        project.getColorPalette(), project.getCustomNote(),
                        resolvedRefsForGeneration);

                if (chainPrompt != null && !chainPrompt.isBlank()) {
                    prompt = chainPrompt;
                    log.info("Mode CHAIN — prompt construit avec succès ({} chars)", prompt.length());
                } else {
                    log.warn("Mode CHAIN — fallback vers CREATIVE");
                    prompt = buildPrompt(project.getStyle(), roomType, project.getBudget(),
                            project.getSofaColor(), project.getSofaType(), project.getSofaMaterial(),
                            project.getColorPalette(), project.getFloorMaterial(), project.getWallFinish(),
                            project.getTableMaterial(), project.getAccessories(),
                            Boolean.TRUE.equals(project.getKeepExisting()),
                            project.getObjectRefs(), project.getCustomNote(), PromptMode.CREATIVE);
                }
            } else {
                prompt = buildPrompt(project.getStyle(), roomType, project.getBudget(),
                        project.getSofaColor(), project.getSofaType(), project.getSofaMaterial(),
                        project.getColorPalette(), project.getFloorMaterial(), project.getWallFinish(),
                        project.getTableMaterial(), project.getAccessories(),
                        Boolean.TRUE.equals(project.getKeepExisting()),
                        project.getObjectRefs(), project.getCustomNote(), promptMode);
            }

            String negativePrompt = "blurry, distorted, unrealistic, bad quality, watermark, text";
            log.info("Prompt ({}): {}", promptMode, prompt);

            String resultImageUrl;
            AiModel usedModel;

            if (useFlux) {
                // ── Flux.1 dev via Replicate ─────────────────────────────────
                // Si l'URL est localhost, on passe les bytes en base64 (Replicate supporte data URI).
                // En prod S3, l'URL publique est utilisée directement.
                String imageUrlForFlux = project.getOriginalImageUrl();
                log.info("Lancement génération Flux.1 (Replicate) — image: {}", imageUrlForFlux);
                resultImageUrl = replicateService.generateImageToImage(
                        imageUrlForFlux, imageBytes, prompt, negativePrompt);
                usedModel = AiModel.FLUX;

            } else if (useQwen) {
                // ── Wan2.7 via DashScope ─────────────────────────────────────
                log.info("Lancement génération Wan2.7 (Qwen)...");
                resultImageUrl = qwenService.generateImageToImage(
                        project.getOriginalImageUrl(), imageBytes, prompt, negativePrompt,
                        resolvedRefsForGeneration);
                usedModel = AiModel.QWEN;

            } else if (useChatGpt) {
                // ── gpt-image-2 — pipeline structuré : analyse JSON → prompt d'édition → image
                log.info("ChatGPT pipeline — étape 1 : analyse structurée de la pièce...");

                Map<String, Object> structuredAnalysis = openAiService.analyzeRoomStructured(
                        project.getOriginalImageUrl(), imageBytes);
                log.info("ChatGPT analyse OK: {}", structuredAnalysis.get("roomType"));

                String chatGptPrompt = buildChatGptEditingPrompt(
                        structuredAnalysis,
                        project.getStyle(), roomType,
                        project.getColorPalette(), project.getCustomNote(),
                        resolvedRefsForGeneration);

                int wordCount = chatGptPrompt.trim().split("\\s+").length;
                log.info("ChatGPT prompt d'édition — {} mots:\n{}", wordCount, chatGptPrompt);

                String imgSize       = project.getImageSize()       != null ? project.getImageSize()       : "auto";
                String imgQuality    = project.getImageQuality()    != null ? project.getImageQuality()    : "auto";
                String imgFormat     = project.getImageFormat()     != null ? project.getImageFormat()     : "jpeg";
                Integer imgCompress  = project.getImageCompression() != null ? project.getImageCompression() : 85;
                String imgBackground = project.getImageBackground() != null ? project.getImageBackground() : "auto";

                log.info("gpt-image-2 — size={} quality={} format={} compression={} bg={}",
                         imgSize, imgQuality, imgFormat, imgCompress, imgBackground);

                byte[] generatedBytes = openAiService.generateImageToImage(
                        chatGptPrompt, imageBytes,
                        imgSize, imgQuality, imgFormat, imgCompress, imgBackground,
                        resolvedRefsForGeneration);
                resultImageUrl = storageService.saveGeneratedImage(
                        generatedBytes, project.getUser().getId());
                usedModel = AiModel.CHATGPT;

            } else {
                throw new IllegalStateException("Modèle IA non supporté : " + aiModel);
            }

            log.info("Image générée: {}", resultImageUrl);
            int processingTime = (int) (Instant.now().toEpochMilli() - startTime);

            // ── 5. Sauvegarder génération + DONE ─────────────────────────────
            final String finalUrl    = resultImageUrl;
            final AiModel finalModel = usedModel;
            final String finalPrompt = prompt;
            final byte[] finalImageBytes = imageBytes;

            // 5a — Génération + DONE en DB
            Generation generation = transactionTemplate.execute(tx -> {
                Generation gen = Generation.builder()
                        .project(project)
                        .resultImageUrl(finalUrl)
                        .prompt(finalPrompt)
                        .negativePrompt(negativePrompt)
                        .model(finalModel)
                        .processingTimeMs(processingTime)
                        .build();
                generationRepository.save(gen);
                project.setStatus(ProjectStatus.DONE);
                projectRepository.save(project);
                return gen;
            });

            log.info("✓ Projet {} → DONE en {}ms", projectId, processingTime);

            // 5b — Recherche produits HORS transaction (appel ChatGPT Vision = long)
            boolean searchOnline = Boolean.TRUE.equals(project.getProductSearchEnabled())
                    || appProperties.getProductSearch().isEnabled();

            List<Product> products;
            if (searchOnline && generation != null) {
                log.info("Recherche produits via ChatGPT Vision — projet {}", projectId);
                List<Product> online = productSearchService.searchProducts(
                        generation,
                        project.getStyle(),
                        project.getPreferredBrands(),
                        finalUrl,
                        finalImageBytes
                );
                products = online.isEmpty()
                        ? generateProductSuggestions(generation, project.getStyle(), project.getBudget())
                        : online;
            } else if (generation != null) {
                products = generateProductSuggestions(generation, project.getStyle(), project.getBudget());
            } else {
                products = List.of();
            }

            // 5c — Sauvegarde produits
            if (!products.isEmpty()) {
                final List<Product> toSave = products;
                transactionTemplate.execute(tx -> {
                    productRepository.saveAll(toSave);
                    return null;
                });
            }

        } catch (Exception e) {
            log.error("✗ Erreur génération projet {}: {}", projectId, e.getMessage(), e);
            int processingTime = (int) (Instant.now().toEpochMilli() - startTime);

            transactionTemplate.execute(tx -> {
                project.setStatus(ProjectStatus.FAILED);
                projectRepository.save(project);

                Generation failedGen = Generation.builder()
                        .project(project)
                        .prompt("N/A")
                        .model(project.getAiModel() != null ? project.getAiModel() : AiModel.QWEN)
                        .errorMessage(e.getMessage())
                        .processingTimeMs(processingTime)
                        .build();
                generationRepository.save(failedGen);
                return null;
            });
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Prompt builder
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Pour chaque objet de référence, tente de lire les bytes locaux et de les encoder en base64.
     * Si les bytes ne sont pas disponibles (mode prod S3), on conserve l'URL publique.
     * Ajoute une clé "imageParam" utilisée par QwenService pour passer l'image à l'API.
     */
    private List<Map<String, String>> resolveObjectRefs(List<Map<String, String>> objectRefs) {
        if (objectRefs == null || objectRefs.isEmpty()) return List.of();

        List<Map<String, String>> resolved = new ArrayList<>();
        for (Map<String, String> ref : objectRefs) {
            Map<String, String> copy = new HashMap<>(ref);
            String imageKey = ref.get("imageKey");
            String imageUrl = ref.getOrDefault("imageUrl", "");

            if (imageKey != null && !imageKey.isBlank()) {
                byte[] refBytes = storageService.getImageBytes(imageKey);
                if (refBytes != null && refBytes.length > 0) {
                    String b64 = Base64.getEncoder().encodeToString(refBytes);
                    copy.put("imageParam", "data:image/jpeg;base64," + b64);
                    log.info("Objet référence '{}' → base64 ({} bytes)", ref.get("title"), refBytes.length);
                } else {
                    copy.put("imageParam", imageUrl);
                    log.info("Objet référence '{}' → URL publique", ref.get("title"));
                }
            } else {
                copy.put("imageParam", imageUrl);
            }
            resolved.add(copy);
        }
        return resolved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Prompt builder — format unifié pour tous les modèles (Wan2.7, Flux, ChatGPT)
    // Structure : Transform → Replace → Add → Colors → User prefs → Preserve → Quality
    // ──────────────────────────────────────────────────────────────────────────

    private static final String PRESERVE_FOOTER =
            "\n\nKeep the original image exactly as it is. Do not change the image size, aspect ratio, " +
            "composition, characters, facial features, objects, positions, or any visual elements. " +
            "Only apply the requested artistic style and update the background/decor. " +
            "Everything else must remain identical to the original image.";

    private static final String QUALITY_FOOTER =
            "\n\nPhotorealistic interior design, realistic shadows, cozy atmosphere.";

    // ── Label lisible pour le style (utilisé par le mode PRO) ────────────────
    private String styleLabel(DecorationStyle style) {
        return style.name().replace("_", " ").toLowerCase()
                .substring(0, 1).toUpperCase()
                + style.name().replace("_", " ").toLowerCase().substring(1);
    }

    private String buildPrompt(DecorationStyle style, String roomType, BigDecimal budget,
                                String sofaColor, String sofaType, String sofaMaterial,
                                String colorPalette, String floorMaterial, String wallFinish,
                                String tableMaterial, String accessories, boolean keepExisting,
                                List<Map<String, String>> objectRefs, String customNote,
                                PromptMode promptMode) {

        String rt = (roomType != null && !roomType.isBlank()) ? roomType : "living room";

        // ── Mode PRO — system prompt "interior designer" strict ───────────────
        if (PromptMode.PRO.equals(promptMode)) {
            String extraPrefs = buildExtraPrefs(colorPalette, customNote, objectRefs);
            return buildProPrompt(style, rt, extraPrefs);
        }

        String base = buildCreativeBase(style, rt);

        // ── Préférences utilisateur (mode CREATIVE) ───────────────────────────
        StringBuilder userPrefs = new StringBuilder();
        if (sofaType     != null) userPrefs.append("\nReplace the sofa with a ").append(sofaType).append(".");
        if (sofaColor    != null) userPrefs.append("\nSofa color: ").append(sofaColor).append(".");
        if (sofaMaterial != null) userPrefs.append("\nSofa material: ").append(sofaMaterial).append(".");
        if (tableMaterial!= null) userPrefs.append("\nCoffee table material: ").append(tableMaterial).append(".");
        if (floorMaterial!= null) userPrefs.append("\nFloor material: ").append(floorMaterial).append(".");
        if (wallFinish   != null) userPrefs.append("\nWall finish: ").append(wallFinish).append(".");
        if (accessories  != null && !accessories.isBlank())
            userPrefs.append("\nAdd these accessories: ").append(accessories.replace(",", ", ")).append(".");
        if (keepExisting)
            userPrefs.append("\nKeep all existing furniture and materials. Only add or replace the elements listed above.");
        userPrefs.append(buildExtraPrefs(colorPalette, customNote, objectRefs));

        String userPrefsBlock = userPrefs.length() > 0 ? "\n" + userPrefs : "";
        return base + userPrefsBlock + "\n" + PRESERVE_FOOTER + QUALITY_FOOTER;
    }

    // ── Switch des bases créatives par style ──────────────────────────────────
    private String buildCreativeBase(DecorationStyle style, String rt) {
        return switch (style) {

            case SCANDINAVIAN ->
                "Transform this real " + rt + " into a modern Scandinavian interior design.\n\n"
                + "Replace the current sofa with a large beige sectional fabric sofa.\n"
                + "Add a wooden oak coffee table.\n"
                + "Add warm indirect lighting and elegant black floor lamps.\n"
                + "Add indoor plants in terracotta pots and minimalist decorations.\n"
                + "Add a cream-colored area rug and linen curtains.\n\n"
                + "Use beige, white and oak wood colors.";

            case MODERN_LUXURY ->
                "Transform this real " + rt + " into a modern luxury interior design.\n\n"
                + "Replace the current sofa with a deep navy velvet sofa with gold cushions.\n"
                + "Add a large white marble coffee table with brass legs.\n"
                + "Add a dramatic chandelier and gold wall sconces.\n"
                + "Add a large abstract art painting on the wall.\n"
                + "Add velvet armchairs and a thick cream and gold area rug.\n\n"
                + "Use navy blue, gold and marble white colors.";

            case MINIMALIST ->
                "Transform this real " + rt + " into a pure minimalist interior design.\n\n"
                + "Replace the current sofa with a single low-profile sofa in off-white fabric.\n"
                + "Add a slim natural wood coffee table.\n"
                + "Add one subtle abstract artwork on the wall.\n"
                + "Add one potted plant in a geometric pot and a clean geometric area rug.\n\n"
                + "Use white, light grey and natural wood colors.";

            case JAPANESE_ZEN ->
                "Transform this real " + rt + " into a Japanese zen interior design.\n\n"
                + "Replace the current furniture with low natural wood pieces and floor cushions.\n"
                + "Add a large natural fiber tatami-style rug.\n"
                + "Add a bonsai tree on a wooden stand and a ceramic vase with dried branches.\n"
                + "Add a paper lantern and linen cushions in earth tones.\n\n"
                + "Use natural wood, beige, sand and dark green colors.";

            case ARABIC_MODERN ->
                "Transform this real " + rt + " into a modern Arabic interior design.\n\n"
                + "Replace the current sofa with a large low-profile sofa in jewel-toned fabric.\n"
                + "Add a geometric patterned area rug in deep red, gold and navy.\n"
                + "Add ornate brass floor lanterns and a decorative pendant lamp.\n"
                + "Add plush embroidered cushions and an ornamental brass tray with candles.\n\n"
                + "Use deep red, gold, royal blue and brass colors.";

            case GAMER_SETUP ->
                "Transform this real " + rt + " into a premium gaming setup interior design.\n\n"
                + "Replace the current desk with a large curved gaming desk.\n"
                + "Add an ergonomic gaming chair in black and red.\n"
                + "Add a triple monitor setup on adjustable arms and a mechanical keyboard.\n"
                + "Add RGB LED light strips along the desk edge and behind the monitors.\n"
                + "Add gaming artwork on the wall and RGB accent lighting on shelves.\n\n"
                + "Use black, dark grey and RGB neon colors.";

            case COZY ->
                "Transform this real " + rt + " into a warm cozy hygge interior design.\n\n"
                + "Replace the current sofa with a large plush oversized sofa in warm terracotta fabric.\n"
                + "Add chunky knit blankets and soft throw pillows in earth tones.\n"
                + "Add an Edison-bulb floor lamp and clusters of candles on the coffee table.\n"
                + "Add a tall bookshelf filled with books and a thick sheepskin area rug.\n\n"
                + "Use terracotta, mustard yellow, warm beige and brown colors.";

            case INDUSTRIAL ->
                "Transform this real " + rt + " into an industrial loft interior design.\n\n"
                + "Replace the current sofa with a large dark leather sofa in charcoal.\n"
                + "Add a metal-frame coffee table with a reclaimed wood top.\n"
                + "Add Edison-bulb cage floor lamps and a metal pipe shelving unit.\n"
                + "Add a vintage metal wall clock and a dark wool area rug.\n\n"
                + "Use dark charcoal, raw metal, reclaimed wood and black colors.";

            case SMART_OFFICE ->
                "Transform this real " + rt + " into a smart productive home office interior design.\n\n"
                + "Replace the current furniture with an ergonomic sit-stand desk and a premium mesh chair.\n"
                + "Add a large curved monitor on an adjustable arm.\n"
                + "Add floating minimal wall shelves with books and a small plant.\n"
                + "Add a smart desk lamp with a wireless charging pad.\n\n"
                + "Use white, light grey and natural wood colors.";

            case DEVELOPER_SETUP ->
                "Transform this real " + rt + " into a senior developer home workspace interior design.\n\n"
                + "Replace the current furniture with a wide ultrawide curved monitor on an adjustable arm.\n"
                + "Add a dark premium ergonomic chair and a mechanical keyboard on a large desk mat.\n"
                + "Add floating walnut shelves with technical programming books.\n"
                + "Add subtle RGB accent lighting behind the monitor.\n\n"
                + "Use dark walnut wood, charcoal, white and subtle RGB colors.";

            case MODERN ->
                "Transform this real " + rt + " into a modern interior design.\n\n"
                + "Replace the current furniture with a sleek low-profile sofa in neutral grey or white.\n"
                + "Add a minimalist geometric coffee table in dark metal or glass.\n"
                + "Add recessed LED lighting and a statement pendant lamp.\n"
                + "Add abstract monochrome artwork and a minimal area rug.\n\n"
                + "Use white, grey, black and warm wood tones.";

            case BOHEMIAN ->
                "Transform this real " + rt + " into a bohemian interior design.\n\n"
                + "Replace the current sofa with a plush low sofa covered with layered textiles and cushions.\n"
                + "Add a macramé wall hanging and layered vintage rugs.\n"
                + "Add rattan furniture, hanging plants and woven baskets.\n"
                + "Add colorful throw pillows, candles and eclectic decorative objects.\n\n"
                + "Use terracotta, burnt orange, deep teal and warm gold tones.";

            case MID_CENTURY ->
                "Transform this real " + rt + " into a mid-century modern interior design.\n\n"
                + "Replace the current furniture with an iconic low-slung sofa in mustard or olive fabric.\n"
                + "Add a teak or walnut coffee table with tapered legs.\n"
                + "Add an arc floor lamp with a large dome shade.\n"
                + "Add abstract geometric artwork and an organic shaped area rug.\n\n"
                + "Use mustard yellow, olive green, teak wood and warm white tones.";

            case CONTEMPORARY ->
                "Transform this real " + rt + " into a contemporary interior design.\n\n"
                + "Replace the current furniture with a bold modular sofa in a statement color.\n"
                + "Add a sculptural coffee table and oversized pendant lights.\n"
                + "Add a large-format abstract artwork as a focal point.\n"
                + "Add indoor tall plants and metallic decorative accents.\n\n"
                + "Use deep jewel tones, brass, black and off-white.";

            case JAPANDI ->
                "Transform this real " + rt + " into a Japandi interior design.\n\n"
                + "Replace the current furniture with a low natural wood sofa with clean linen cushions.\n"
                + "Add a simple wooden coffee table with wabi-sabi imperfections.\n"
                + "Add a ceramic vase with a single dried branch and a paper lantern.\n"
                + "Add a neutral linen area rug and minimal wall art.\n\n"
                + "Use warm sand, natural oak, muted sage and off-white tones.";

            case VINTAGE ->
                "Transform this real " + rt + " into a vintage interior design.\n\n"
                + "Replace the current furniture with a button-tufted velvet sofa in dusty rose or emerald.\n"
                + "Add a carved wood coffee table and an ornate floor mirror.\n"
                + "Add a crystal chandelier and vintage table lamps with fabric shades.\n"
                + "Add antique picture frames, vintage books and decorative trinkets.\n\n"
                + "Use dusty rose, deep emerald, gold and aged oak tones.";

            case MAXIMALIST ->
                "Transform this real " + rt + " into a maximalist interior design.\n\n"
                + "Replace the current sofa with a rich velvet sofa in deep jewel tones.\n"
                + "Add layered Persian rugs and multiple patterned throw pillows.\n"
                + "Add an eclectic gallery wall with mixed frames and artwork.\n"
                + "Add lush indoor plants, sculptural lamps and abundant decorative objects.\n\n"
                + "Use deep burgundy, royal blue, emerald, gold and rich pattern tones.";

            case NEOCLASSIC ->
                "Transform this real " + rt + " into a neoclassical interior design.\n\n"
                + "Replace the current furniture with a French-style sofa with carved wood frame.\n"
                + "Add marble-top console tables and gilded ornamental mirrors.\n"
                + "Add a crystal chandelier and tall white pillar candles.\n"
                + "Add symmetrical decorative arrangements and classical artwork.\n\n"
                + "Use ivory white, pale gold, marble and soft grey tones.";

            case FARMHOUSE ->
                "Transform this real " + rt + " into a farmhouse interior design.\n\n"
                + "Replace the current furniture with a linen-covered rolled-arm sofa in cream or grey.\n"
                + "Add a reclaimed wood coffee table and open wooden shelving.\n"
                + "Add mason jar arrangements and galvanized metal decorative accents.\n"
                + "Add a large jute area rug and white shiplap-style wall decor.\n\n"
                + "Use crisp white, weathered grey, warm wood and sage green tones.";

            case SKI_CHALET ->
                "Transform this real " + rt + " into a ski chalet interior design.\n\n"
                + "Replace the current furniture with an oversized knit sofa with chunky fur throws.\n"
                + "Add a rugged stone fireplace surround and thick wooden beams visible on ceiling.\n"
                + "Add antler-style decorative objects and alpine artwork.\n"
                + "Add a thick sheepskin rug and warm lantern-style lighting.\n\n"
                + "Use deep forest green, warm chestnut, stone grey and cream wool tones.";

            case ART_DECO ->
                "Transform this real " + rt + " into an Art Deco interior design.\n\n"
                + "Replace the current furniture with a bold geometric sofa in black velvet.\n"
                + "Add a lacquered black and gold sunburst mirror above the focal wall.\n"
                + "Add a tiered geometric chandelier and brass floor lamps.\n"
                + "Add a chevron-pattern area rug and symmetrical decorative arrangements.\n\n"
                + "Use midnight black, champagne gold, ivory and deep teal tones.";

            case FRENCH_COUNTRY ->
                "Transform this real " + rt + " into a French country interior design.\n\n"
                + "Replace the current furniture with a linen upholstered sofa in soft sage or cream.\n"
                + "Add a distressed wood coffee table and antique-style open shelving.\n"
                + "Add a large ceramic urn with lavender and fresh flower arrangements.\n"
                + "Add toile de jouy cushions and lace curtains.\n\n"
                + "Use dusty lavender, sage green, warm cream and aged oak tones.";

            case RUSTIC ->
                "Transform this real " + rt + " into a rustic interior design.\n\n"
                + "Replace the current furniture with a heavy leather sofa in dark cognac.\n"
                + "Add a thick live-edge wood slab coffee table.\n"
                + "Add exposed wooden ceiling beams and a wrought iron chandelier.\n"
                + "Add woven baskets, stacked logs as decor and cowhide rug.\n\n"
                + "Use dark cognac, raw oak, iron grey and deep earthy brown tones.";

            case MEDIEVAL ->
                "Transform this real " + rt + " into a medieval-inspired interior design.\n\n"
                + "Replace the current furniture with a heavy carved wood sofa with dark velvet cushions.\n"
                + "Add stone-textured decorative elements and wrought iron candelabras.\n"
                + "Add tapestry-style wall hangings and gothic arch decorative frames.\n"
                + "Add thick wool rugs in deep jewel tones and dramatic pendant lanterns.\n\n"
                + "Use deep burgundy, charcoal stone, dark oak and aged gold tones.";
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mode PRO — dynamique : style + room type + déco + préférences
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Extrait uniquement le bloc de décoration du style (sans l'intro "Transform this real…").
     * Utilisé par le mode PRO pour injecter des instructions dynamiques.
     */
    private String buildStyleDecorationContent(DecorationStyle style, String roomType) {
        String full = buildCreativeBase(style, roomType);
        int split = full.indexOf("\n\n");
        return split >= 0 ? full.substring(split + 2) : full;
    }

    /**
     * Construit le bloc des préférences complémentaires (palette, note, objets).
     * Partagé entre le mode CREATIVE et PRO.
     */
    private String buildExtraPrefs(String colorPalette,
                                   String customNote,
                                   List<Map<String, String>> objectRefs) {
        StringBuilder sb = new StringBuilder();
        if (colorPalette != null && !colorPalette.isBlank())
            sb.append("\nColor palette preference: ").append(colorPalette).append(".");
        if (objectRefs != null && !objectRefs.isEmpty()) {
            sb.append("\nIntegrate the following reference objects into the room:");
            for (int i = 0; i < objectRefs.size(); i++) {
                String title = objectRefs.get(i).getOrDefault("title", "Object " + (i + 1));
                sb.append("\n- Place the '").append(title)
                  .append("' from reference image ").append(i + 2)
                  .append(" realistically in the room.");
            }
        }
        if (customNote != null && !customNote.isBlank())
            sb.append("\nUser specific request: ").append(customNote.trim());
        return sb.toString();
    }

    private String buildProPrompt(DecorationStyle style, String roomType, String extraPrefs) {
        String decorationContent = buildStyleDecorationContent(style, roomType);

        return "You are a professional interior designer.\n\n"
             + "The uploaded image is the reference room.\n\n"
             + "STRICT RULES:\n"
             + "- Preserve room architecture exactly.\n"
             + "- Preserve walls, windows, doors, ceiling and floor.\n"
             + "- Preserve camera angle and perspective.\n"
             + "- Preserve room dimensions.\n"
             + "- Do not generate a new room.\n"
             + "- Do not move structural elements.\n\n"
             + "Only modify:\n"
             + "- furniture\n"
             + "- decoration\n"
             + "- lighting\n"
             + "- colors\n"
             + "- accessories\n\n"
             + "Style: " + styleLabel(style) + "\n"
             + "Room type: " + roomType + "\n\n"
             + "Apply these decoration changes:\n"
             + decorationContent
             + (extraPrefs.isBlank() ? "" : "\n" + extraPrefs)
             + "\n\nShow product labels and estimated prices.\n\n"
             + "The final image must look like the same room after renovation.\n"
             + "Photorealistic.\n"
             + "Interior design magazine quality.\n"
             + "Ultra detailed.";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ChatGPT editing prompt — 3 sections, 150-250 mots
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Construit un prompt d'édition structuré (150-250 mots) pour gpt-image-2.
     *
     * <p>Architecture en 3 sections :
     * <ol>
     *   <li>PRESERVATION RULES (~60%) — architecture exacte de la pièce extraite du JSON d'analyse</li>
     *   <li>STYLE TRANSFORMATION (~30%) — mobilier et décoration du style cible</li>
     *   <li>RENDERING QUALITY (~10%) — qualité photo-réaliste magazine</li>
     * </ol>
     *
     * @param analysis   Résultat de {@link OpenAiService#analyzeRoomStructured}
     * @param style      Style de décoration sélectionné
     * @param roomType   Type de pièce (forcé ou détecté)
     * @param colorPalette Palette de couleurs optionnelle
     * @param customNote  Note libre de l'utilisateur
     * @param objectRefs  Objets de référence à intégrer
     * @return Prompt d'édition prêt à être envoyé à gpt-image-2
     */
    @SuppressWarnings("unchecked")
    private String buildChatGptEditingPrompt(Map<String, Object> analysis,
                                              DecorationStyle style, String roomType,
                                              String colorPalette, String customNote,
                                              List<Map<String, String>> objectRefs) {
        StringBuilder sb = new StringBuilder();

        // ── SECTION 1 : Preservation rules ───────────────────────────────────
        sb.append("IMAGE EDITING TASK. Renovate the existing room — do NOT generate a new room.\n\n");
        sb.append("PRESERVE EXACTLY:\n");
        sb.append("- Room geometry, spatial proportions and dimensions\n");

        // Architecture depuis l'analyse JSON
        if (analysis != null) {
            Object archObj = analysis.get("architecture");
            if (archObj instanceof Map) {
                Map<String, Object> arch = (Map<String, Object>) archObj;

                appendListField(sb, "- Walls", arch.get("walls"), 2);
                appendListField(sb, "- Windows (keep position and size)", arch.get("windows"), 2);
                appendListField(sb, "- Doors (keep position)", arch.get("doors"), 1);

                Object floor = arch.get("floor");
                if (floor != null && !floor.toString().isBlank())
                    sb.append("- Floor: ").append(floor).append("\n");

                Object ceiling = arch.get("ceiling");
                if (ceiling != null && !ceiling.toString().isBlank())
                    sb.append("- Ceiling: ").append(ceiling).append("\n");
            }

            Object cameraObj = analysis.get("camera");
            if (cameraObj instanceof Map) {
                Map<String, Object> camera = (Map<String, Object>) cameraObj;
                String angle       = camera.getOrDefault("angle",       "").toString();
                String perspective = camera.getOrDefault("perspective", "").toString();
                if (!angle.isBlank() || !perspective.isBlank())
                    sb.append("- Camera angle and perspective: ")
                      .append(angle).append(" ").append(perspective.trim()).append("\n");
            }

            Object lighting = analysis.get("lighting");
            if (lighting != null && !lighting.toString().isBlank())
                sb.append("- Lighting sources position: ").append(lighting).append("\n");
        } else {
            sb.append("- All walls, windows, doors, floor and ceiling\n");
            sb.append("- Camera angle and perspective\n");
        }

        // ── SECTION 2 : Style transformation ─────────────────────────────────
        sb.append("\nAPPLY ").append(styleLabel(style).toUpperCase()).append(" INTERIOR DESIGN:\n");

        // Prendre les 4 premières lignes non vides du contenu déco du style
        String decorContent = buildStyleDecorationContent(style, roomType);
        String[] decorLines = decorContent.split("\n");
        int usedLines = 0;
        for (String line : decorLines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            sb.append("- ").append(trimmed).append("\n");
            if (++usedLines >= 4) break;
        }

        // Préférences supplémentaires
        if (colorPalette != null && !colorPalette.isBlank())
            sb.append("- Color palette: ").append(colorPalette).append("\n");
        if (customNote != null && !customNote.isBlank())
            sb.append("- ").append(customNote.trim()).append("\n");

        // ── SECTION 2b : Objets de référence ─────────────────────────────────
        if (objectRefs != null && !objectRefs.isEmpty()) {
            sb.append("\nADD THESE SPECIFIC REFERENCE OBJECTS INTO THE SCENE");
            sb.append(" (visual reference images are provided — match them exactly):\n");
            for (Map<String, String> ref : objectRefs) {
                String title = ref.getOrDefault("title", "reference object");
                sb.append("- Place the exact '").append(title)
                  .append("' shown in the reference image into the room. ")
                  .append("Preserve its design, color, material and shape precisely.\n");
            }
        }

        // ── SECTION 3 : Rendering quality ────────────────────────────────────
        sb.append("\nRENDERING: photorealistic, real-world materials and textures, ")
          .append("natural lighting, interior design photography, high-end magazine quality.");

        return sb.toString();
    }

    /** Ajoute un champ liste de l'analyse JSON sous forme de bullet point, limité à {@code maxItems}. */
    @SuppressWarnings("unchecked")
    private void appendListField(StringBuilder sb, String label, Object value, int maxItems) {
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            if (!list.isEmpty()) {
                List<Object> limited = list.subList(0, Math.min(maxItems, list.size()));
                sb.append(label).append(": ").append(String.join(", ", limited.stream()
                        .map(Object::toString).toList())).append("\n");
            }
        } else if (value != null && !value.toString().isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Suggestions produits
    // ──────────────────────────────────────────────────────────────────────────

    private List<Product> generateProductSuggestions(Generation generation, DecorationStyle style, BigDecimal budget) {
        return switch (style) {
            case SCANDINAVIAN -> List.of(
                    buildProduct(generation, "Canapé EKTORP 3 places", ProductCategory.SOFA,  ProductBrand.IKEA,   new BigDecimal("599"), "https://www.ikea.com"),
                    buildProduct(generation, "Table basse HEMNES",     ProductCategory.TABLE,  ProductBrand.IKEA,   new BigDecimal("199"), "https://www.ikea.com"),
                    buildProduct(generation, "Lampadaire HEKTAR",       ProductCategory.LAMP,   ProductBrand.IKEA,   new BigDecimal("79"),  "https://www.ikea.com"),
                    buildProduct(generation, "Tapis STOENSE",            ProductCategory.CARPET, ProductBrand.IKEA,   new BigDecimal("129"), "https://www.ikea.com")
            );
            case GAMER_SETUP -> List.of(
                    buildProduct(generation, "Bureau gaming FREDDE",    ProductCategory.DESK,   ProductBrand.IKEA,   new BigDecimal("299"), "https://www.ikea.com"),
                    buildProduct(generation, "Chaise gaming DXRacer",   ProductCategory.CHAIR,  ProductBrand.AMAZON, new BigDecimal("399"), "https://www.amazon.fr"),
                    buildProduct(generation, "Bande LED RGB Govee",     ProductCategory.LAMP,   ProductBrand.AMAZON, new BigDecimal("49"),  "https://www.amazon.fr")
            );
            default -> List.of(
                    buildProduct(generation, "Canapé 3 places",         ProductCategory.SOFA,   ProductBrand.IKEA,   new BigDecimal("499"), "https://www.ikea.com"),
                    buildProduct(generation, "Lampe de sol",             ProductCategory.LAMP,   ProductBrand.ACTION, new BigDecimal("39"),  "https://www.action.com"),
                    buildProduct(generation, "Plante d'intérieur",      ProductCategory.PLANT,  ProductBrand.IKEA,   new BigDecimal("29"),  "https://www.ikea.com")
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
