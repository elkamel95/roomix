package com.homegpt.api.service;

import com.homegpt.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final AppProperties appProperties;

    public String uploadImage(MultipartFile file, UUID userId) {
        validateImage(file);

        String extension = getExtension(file.getOriginalFilename());
        String key = String.format("users/%s/images/%s.%s", userId, UUID.randomUUID(), extension);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(appProperties.getStorage().getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Image uploadée: {}", key);
            return key;

        } catch (IOException e) {
            throw new RuntimeException("Erreur upload image: " + e.getMessage(), e);
        }
    }

    public String getPublicUrl(String key) {
        String endpoint = appProperties.getStorage().getEndpoint();
        String bucket = appProperties.getStorage().getBucket();
        return String.format("%s/%s/%s", endpoint, bucket, key);
    }

    public void deleteImage(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(appProperties.getStorage().getBucket())
                    .key(key)
                    .build());
            log.info("Image supprimée: {}", key);
        } catch (Exception e) {
            log.warn("Impossible de supprimer l'image {}: {}", key, e.getMessage());
        }
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg")
                && !contentType.equals("image/png")
                && !contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("Format non supporté. Utilisez JPG, PNG ou WEBP.");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Image trop lourde. Maximum 10 MB.");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
