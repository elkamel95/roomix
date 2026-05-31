package com.roomix.api.service;

import com.roomix.api.exception.QuotaExceededException;
import com.roomix.api.exception.ResourceNotFoundException;
import com.roomix.api.model.dto.request.CreateProjectRequest;
import com.roomix.api.model.dto.response.GenerationResponse;
import com.roomix.api.model.dto.response.ProductResponse;
import com.roomix.api.model.dto.response.ProjectResponse;
import com.roomix.api.model.entity.Generation;
import com.roomix.api.model.entity.Project;
import com.roomix.api.model.entity.User;
import com.roomix.api.model.enums.PlanType;
import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.ProductCategory;
import com.roomix.api.model.enums.ProjectStatus;
import com.roomix.api.repository.GenerationRepository;
import com.roomix.api.repository.ProductRepository;
import com.roomix.api.repository.ProjectRepository;
import com.roomix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final GenerationRepository generationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AiOrchestrationService aiOrchestrationService;

    private static final int FREE_DAILY_LIMIT = 3000;

    @Transactional
    public ProjectResponse createProject(String userEmail, MultipartFile image,
                                         CreateProjectRequest request,
                                         List<MultipartFile> objectImages,
                                         List<String> objectTitles) {
        User user = findUser(userEmail);
        checkAndIncrementQuota(user);

        String imageKey = storageService.uploadImage(image, user.getId());
        String imageUrl = storageService.getPublicUrl(imageKey);

        // ── Upload des objets de référence ────────────────────────────────────
        List<Map<String, String>> objectRefs = new ArrayList<>();
        for (int i = 0; i < Math.min(objectImages.size(), 3); i++) {
            MultipartFile objFile  = objectImages.get(i);
            String        objTitle = i < objectTitles.size() ? objectTitles.get(i) : "Objet " + (i + 1);
            try {
                String objKey = storageService.uploadImage(objFile, user.getId());
                String objUrl = storageService.getPublicUrl(objKey);
                Map<String, String> ref = new HashMap<>();
                ref.put("title",    objTitle);
                ref.put("imageKey", objKey);
                ref.put("imageUrl", objUrl);
                objectRefs.add(ref);
            } catch (Exception e) {
                log.warn("Impossible d'uploader l'objet de référence '{}': {}", objTitle, e.getMessage());
            }
        }

        Project project = Project.builder()
                .user(user)
                .name(request.getName() != null ? request.getName() : "Mon projet")
                .originalImageUrl(imageUrl)
                .originalImageKey(imageKey)
                .style(request.getStyle())
                .budget(request.getBudget())
                .status(ProjectStatus.PENDING)
                .aiModel(request.getAiModel() != null
                        ? request.getAiModel()
                        : com.roomix.api.model.enums.AiModel.QWEN)
                .sofaColor(request.getSofaColor())
                .sofaType(request.getSofaType())
                .sofaMaterial(request.getSofaMaterial())
                .colorPalette(request.getColorPalette())
                .floorMaterial(request.getFloorMaterial())
                .wallFinish(request.getWallFinish())
                .tableMaterial(request.getTableMaterial())
                .accessories(request.getAccessories())
                .keepExisting(request.getKeepExisting() != null && request.getKeepExisting())
                .roomType(request.getRoomType())
                .customNote(request.getCustomNote())
                .promptMode(request.getPromptMode() != null
                        ? request.getPromptMode()
                        : com.roomix.api.model.enums.PromptMode.CREATIVE)
                .imageSize(request.getImageSize() != null ? request.getImageSize() : "auto")
                .imageQuality(request.getImageQuality() != null ? request.getImageQuality() : "auto")
                .imageFormat(request.getImageFormat() != null ? request.getImageFormat() : "jpeg")
                .imageCompression(request.getImageCompression() != null ? request.getImageCompression() : 85)
                .imageBackground(request.getImageBackground() != null ? request.getImageBackground() : "auto")
                .objectRefs(objectRefs.isEmpty() ? null : objectRefs)
                .build();

        projectRepository.save(project);
        log.info("Projet créé: {} pour user: {}", project.getId(), userEmail);

        // Déclencher le traitement IA APRÈS le commit de la transaction courante.
        // Si aucune transaction n'est active (ex : tests unitaires), appel direct.
        final UUID projectId = project.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiOrchestrationService.processProjectAsync(projectId);
                }
            });
        } else {
            aiOrchestrationService.processProjectAsync(projectId);
        }

        return toProjectResponse(project, null);
    }

    public Page<ProjectResponse> getProjects(String userEmail, int page, int size, ProjectStatus status) {
        User user = findUser(userEmail);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Project> projects = status != null
                ? projectRepository.findByUserAndStatusOrderByCreatedAtDesc(user, status, pageRequest)
                : projectRepository.findByUserOrderByCreatedAtDesc(user, pageRequest);

        return projects.map(p -> {
            Generation lastGen = generationRepository.findTopByProjectOrderByCreatedAtDesc(p).orElse(null);
            return toProjectResponse(p, lastGen);
        });
    }

    public ProjectResponse getProject(String userEmail, UUID projectId) {
        User user = findUser(userEmail);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Projet non trouvé: " + projectId));

        Generation lastGen = generationRepository.findTopByProjectOrderByCreatedAtDesc(project).orElse(null);
        return toProjectResponse(project, lastGen);
    }

    public GenerationResponse getGenerationStatus(String userEmail, UUID projectId) {
        User user = findUser(userEmail);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Projet non trouvé: " + projectId));

        Generation gen = generationRepository.findTopByProjectOrderByCreatedAtDesc(project).orElse(null);

        int progress = switch (project.getStatus()) {
            case PENDING -> 0;
            case PROCESSING -> 50;
            case DONE -> 100;
            case FAILED -> 0;
        };

        return GenerationResponse.builder()
                .id(gen != null ? gen.getId() : null)
                .resultImageUrl(gen != null ? gen.getResultImageUrl() : null)
                .processingTimeMs(gen != null ? gen.getProcessingTimeMs() : null)
                .createdAt(gen != null ? gen.getCreatedAt() : null)
                .status(project.getStatus())
                .progress(progress)
                .errorMessage(gen != null ? gen.getErrorMessage() : null)
                .build();
    }

    public List<ProductResponse> getProducts(String userEmail, UUID projectId) {
        User user = findUser(userEmail);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Projet non trouvé: " + projectId));

        return generationRepository.findTopByProjectOrderByCreatedAtDesc(project)
                .map(gen -> productRepository.findByGeneration(gen).stream()
                        .map(this::toProductResponse)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Transactional
    public void deleteProject(String userEmail, UUID projectId) {
        User user = findUser(userEmail);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Projet non trouvé: " + projectId));

        if (project.getOriginalImageKey() != null) {
            storageService.deleteImage(project.getOriginalImageKey());
        }

        projectRepository.delete(project);
        log.info("Projet supprimé: {}", projectId);
    }

    @Transactional
    public void renameProject(String userEmail, UUID projectId, String newName) {
        User user = findUser(userEmail);
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Projet non trouvé: " + projectId));
        project.setName(newName);
        projectRepository.save(project);
    }

    private void checkAndIncrementQuota(User user) {
        if (user.getPlan() == PlanType.PREMIUM || user.getPlan() == PlanType.PRO) {
            return;
        }

        if (!user.getLastGenerationReset().equals(LocalDate.now())) {
            user.setDailyGenerations(0);
            user.setLastGenerationReset(LocalDate.now());
        }

        if (user.getDailyGenerations() >= FREE_DAILY_LIMIT) {
            throw new QuotaExceededException("Quota gratuit atteint. Passez en Premium pour générer plus.");
        }

        user.setDailyGenerations(user.getDailyGenerations() + 1);
        userRepository.save(user);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    private ProjectResponse toProjectResponse(Project project, Generation generation) {
        GenerationResponse genResponse = generation != null ? GenerationResponse.builder()
                .id(generation.getId())
                .resultImageUrl(generation.getResultImageUrl())
                .processingTimeMs(generation.getProcessingTimeMs())
                .createdAt(generation.getCreatedAt())
                .status(project.getStatus())
                .errorMessage(generation.getErrorMessage())
                .build() : null;

        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .originalImageUrl(project.getOriginalImageUrl())
                .status(project.getStatus())
                .style(project.getStyle())
                .budget(project.getBudget())
                .createdAt(project.getCreatedAt())
                .generation(genResponse)
                .build();
    }

    private ProductResponse toProductResponse(com.roomix.api.model.entity.Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .brand(product.getBrand())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .productUrl(product.getProductUrl())
                .affiliateUrl(product.getAffiliateUrl())
                .imageUrl(product.getImageUrl())
                .inStock(product.getInStock())
                .build();
    }
}
