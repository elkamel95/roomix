package com.roomix.api.service;

import com.roomix.api.exception.QuotaExceededException;
import com.roomix.api.exception.ResourceNotFoundException;
import com.roomix.api.model.dto.request.CreateProjectRequest;
import com.roomix.api.model.dto.response.ProjectResponse;
import com.roomix.api.model.entity.Project;
import com.roomix.api.model.entity.User;
import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.PlanType;
import com.roomix.api.model.enums.ProjectStatus;
import com.roomix.api.repository.GenerationRepository;
import com.roomix.api.repository.ProductRepository;
import com.roomix.api.repository.ProjectRepository;
import com.roomix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService — Tests unitaires")
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private GenerationRepository generationRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private AiOrchestrationService aiOrchestrationService;

    @InjectMocks
    private ProjectService projectService;

    private User freeUser;
    private User premiumUser;

    @BeforeEach
    void setUp() {
        freeUser = User.builder()
                .id(UUID.randomUUID())
                .email("free@roomix.ai")
                .plan(PlanType.FREE)
                .dailyGenerations(0)
                .lastGenerationReset(LocalDate.now())
                .build();

        premiumUser = User.builder()
                .id(UUID.randomUUID())
                .email("premium@roomix.ai")
                .plan(PlanType.PREMIUM)
                .dailyGenerations(0)
                .lastGenerationReset(LocalDate.now())
                .build();
    }

    // ===================== CREATE PROJECT =====================

    @Test
    @DisplayName("createProject — succès utilisateur FREE avec quota disponible")
    void createProject_freeUser_withinQuota() {
        MockMultipartFile image = new MockMultipartFile("image", "room.jpg", "image/jpeg", new byte[100]);
        CreateProjectRequest req = new CreateProjectRequest();
        req.setStyle(DecorationStyle.SCANDINAVIAN);

        when(userRepository.findByEmail("free@roomix.ai")).thenReturn(Optional.of(freeUser));
        when(storageService.uploadImage(any(), any())).thenReturn("users/123/images/abc.jpg");
        when(storageService.getPublicUrl(any())).thenReturn("https://storage.roomix.ai/image.jpg");
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(userRepository.save(any())).thenReturn(freeUser);

        ProjectResponse response = projectService.createProject("free@roomix.ai", image, req);

        assertThat(response).isNotNull();
        assertThat(response.getStyle()).isEqualTo(DecorationStyle.SCANDINAVIAN);
        assertThat(response.getStatus()).isEqualTo(ProjectStatus.PENDING);
        verify(aiOrchestrationService).processProjectAsync(any(UUID.class));
        verify(userRepository).save(freeUser);
    }

    @Test
    @DisplayName("createProject — lève QuotaExceededException si quota FREE épuisé")
    void createProject_freeUser_quotaExceeded() {
        freeUser.setDailyGenerations(3);
        MockMultipartFile image = new MockMultipartFile("image", "room.jpg", "image/jpeg", new byte[100]);
        CreateProjectRequest req = new CreateProjectRequest();
        req.setStyle(DecorationStyle.COZY);

        when(userRepository.findByEmail("free@roomix.ai")).thenReturn(Optional.of(freeUser));

        assertThatThrownBy(() -> projectService.createProject("free@roomix.ai", image, req))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("Premium");

        verify(projectRepository, never()).save(any());
        verify(aiOrchestrationService, never()).processProjectAsync(any());
    }

    @Test
    @DisplayName("createProject — utilisateur PREMIUM bypass le quota")
    void createProject_premiumUser_bypassesQuota() {
        premiumUser.setDailyGenerations(100);
        MockMultipartFile image = new MockMultipartFile("image", "room.jpg", "image/jpeg", new byte[100]);
        CreateProjectRequest req = new CreateProjectRequest();
        req.setStyle(DecorationStyle.MODERN_LUXURY);

        when(userRepository.findByEmail("premium@roomix.ai")).thenReturn(Optional.of(premiumUser));
        when(storageService.uploadImage(any(), any())).thenReturn("key");
        when(storageService.getPublicUrl(any())).thenReturn("https://url");
        when(projectRepository.save(any())).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });

        assertThatNoException().isThrownBy(() ->
                projectService.createProject("premium@roomix.ai", image, req));

        verify(userRepository, never()).save(any());
    }

    // ===================== GET PROJECT =====================

    @Test
    @DisplayName("getProject — lève ResourceNotFoundException si projet inexistant")
    void getProject_notFound_throwsException() {
        when(userRepository.findByEmail("free@roomix.ai")).thenReturn(Optional.of(freeUser));
        when(projectRepository.findByIdAndUser(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject("free@roomix.ai", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProjects — retourne la liste paginée")
    void getProjects_returnsPaginatedList() {
        Project p = Project.builder()
                .id(UUID.randomUUID())
                .name("Salon Scandinave")
                .originalImageUrl("https://url")
                .style(DecorationStyle.SCANDINAVIAN)
                .status(ProjectStatus.DONE)
                .build();

        when(userRepository.findByEmail("free@roomix.ai")).thenReturn(Optional.of(freeUser));
        when(projectRepository.findByUserOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(generationRepository.findTopByProjectOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        var result = projectService.getProjects("free@roomix.ai", 0, 10, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Salon Scandinave");
    }

    // ===================== DELETE =====================

    @Test
    @DisplayName("deleteProject — supprime le projet et l'image storage")
    void deleteProject_deletesProjectAndImage() {
        Project p = Project.builder()
                .id(UUID.randomUUID())
                .originalImageKey("users/123/images/abc.jpg")
                .style(DecorationStyle.COZY)
                .status(ProjectStatus.DONE)
                .build();

        when(userRepository.findByEmail("free@roomix.ai")).thenReturn(Optional.of(freeUser));
        when(projectRepository.findByIdAndUser(any(), any())).thenReturn(Optional.of(p));

        projectService.deleteProject("free@roomix.ai", p.getId());

        verify(storageService).deleteImage("users/123/images/abc.jpg");
        verify(projectRepository).delete(p);
    }
}
