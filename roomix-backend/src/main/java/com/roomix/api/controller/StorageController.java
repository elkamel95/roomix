package com.roomix.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sert les images stockées localement (mode développement, sans S3/Supabase).
 * URL : GET /api/v1/storage/{key...}
 */
@RestController
@RequestMapping("/storage")
@Slf4j
public class StorageController {

    private static final Path LOCAL_UPLOADS_DIR = Path.of("uploads");

    @GetMapping("/**")
    public ResponseEntity<byte[]> serveFile(HttpServletRequest request) {
        // Extraire la clé depuis le path : /api/v1/storage/users/xxx/images/yyy.jpg
        String uri = request.getRequestURI();
        String key = uri.replaceFirst("^.*/storage/", "");

        Path filePath = LOCAL_UPLOADS_DIR.resolve(key).normalize();

        // Sécurité : interdire les path traversal
        if (!filePath.startsWith(LOCAL_UPLOADS_DIR.toAbsolutePath())
                && !filePath.startsWith(LOCAL_UPLOADS_DIR)) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath)) {
            log.warn("Image locale introuvable: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            MediaType mediaType = contentType != null
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.IMAGE_JPEG;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(bytes);
        } catch (IOException e) {
            log.error("Erreur lecture fichier {}: {}", filePath, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
