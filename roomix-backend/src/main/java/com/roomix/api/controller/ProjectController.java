package com.roomix.api.controller;

import com.roomix.api.model.dto.request.CreateProjectRequest;
import com.roomix.api.model.dto.response.GenerationResponse;
import com.roomix.api.model.dto.response.ProductResponse;
import com.roomix.api.model.dto.response.ProjectResponse;
import com.roomix.api.model.enums.ProjectStatus;
import com.roomix.api.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("image") MultipartFile image,
            @Valid @RequestPart("data") CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(userDetails.getUsername(), image, request));
    }

    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) ProjectStatus status) {
        return ResponseEntity.ok(projectService.getProjects(userDetails.getUsername(), page, size, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProject(userDetails.getUsername(), id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<GenerationResponse> status(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getGenerationStatus(userDetails.getUsername(), id));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<List<ProductResponse>> products(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProducts(userDetails.getUsername(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> rename(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        projectService.renameProject(userDetails.getUsername(), id, body.get("name"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        projectService.deleteProject(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
