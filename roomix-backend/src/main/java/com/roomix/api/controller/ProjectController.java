package com.roomix.api.controller;

import com.roomix.api.model.dto.request.CreateProjectRequest;
import com.roomix.api.model.dto.response.GenerationResponse;
import com.roomix.api.model.dto.response.ProductResponse;
import com.roomix.api.model.dto.response.ProjectResponse;
import com.roomix.api.model.enums.AiModel;
import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.PromptMode;
import com.roomix.api.model.enums.ProjectStatus;
import com.roomix.api.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Collections;
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
            @RequestParam("style") String style,
            @RequestParam(value = "aiModel",       defaultValue = "QWEN") String aiModel,
            @RequestParam(value = "name",          required = false) String name,
            @RequestParam(value = "budget",        required = false) BigDecimal budget,
            @RequestParam(value = "sofaColor",     required = false) String sofaColor,
            @RequestParam(value = "sofaType",      required = false) String sofaType,
            @RequestParam(value = "sofaMaterial",  required = false) String sofaMaterial,
            @RequestParam(value = "colorPalette",  required = false) String colorPalette,
            @RequestParam(value = "floorMaterial", required = false) String floorMaterial,
            @RequestParam(value = "wallFinish",    required = false) String wallFinish,
            @RequestParam(value = "tableMaterial", required = false) String tableMaterial,
            @RequestParam(value = "accessories",   required = false) String accessories,
            @RequestParam(value = "keepExisting",  defaultValue = "false") Boolean keepExisting,
            @RequestParam(value = "roomType",      required = false) String roomType,
            @RequestParam(value = "customNote",    required = false) String customNote,
            @RequestParam(value = "promptMode",       defaultValue = "CREATIVE") String promptMode,
            @RequestParam(value = "imageSize",        defaultValue = "auto")     String imageSize,
            @RequestParam(value = "imageQuality",     defaultValue = "auto")     String imageQuality,
            @RequestParam(value = "imageFormat",      defaultValue = "jpeg")     String imageFormat,
            @RequestParam(value = "imageCompression", defaultValue = "85")       Integer imageCompression,
            @RequestParam(value = "imageBackground",  defaultValue = "auto")     String imageBackground,
            @RequestPart(value = "objectImages",        required = false) List<MultipartFile> objectImages,
            @RequestParam(value = "objectTitles",       required = false) List<String> objectTitles,
            @RequestParam(value = "productSearchEnabled", defaultValue = "false") Boolean productSearchEnabled,
            @RequestParam(value = "preferredBrands",    required = false) List<String> preferredBrands,
            @RequestParam(value = "searchItems",        required = false) String searchItemsJson) {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setStyle(DecorationStyle.valueOf(style));
        request.setAiModel(AiModel.valueOf(aiModel));
        request.setName(name);
        request.setBudget(budget);
        request.setSofaColor(sofaColor);
        request.setSofaType(sofaType);
        request.setSofaMaterial(sofaMaterial);
        request.setColorPalette(colorPalette);
        request.setFloorMaterial(floorMaterial);
        request.setWallFinish(wallFinish);
        request.setTableMaterial(tableMaterial);
        request.setAccessories(accessories);
        request.setKeepExisting(keepExisting);
        request.setRoomType(roomType);
        request.setCustomNote(customNote);
        request.setPromptMode(PromptMode.valueOf(promptMode));
        request.setImageSize(imageSize);
        request.setImageQuality(imageQuality);
        request.setImageFormat(imageFormat);
        request.setImageCompression(imageCompression);
        request.setImageBackground(imageBackground);
        request.setProductSearchEnabled(productSearchEnabled);
        if (preferredBrands != null && !preferredBrands.isEmpty()) {
            request.setPreferredBrands(preferredBrands.stream()
                    .map(b -> ProductBrand.valueOf(b.toUpperCase()))
                    .toList());
        }
        request.setSearchItemsJson(searchItemsJson);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(
                        userDetails.getUsername(), image, request,
                        objectImages != null ? objectImages : Collections.emptyList(),
                        objectTitles != null ? objectTitles : Collections.emptyList()));
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
