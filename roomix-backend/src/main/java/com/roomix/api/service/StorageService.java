package com.roomix.api.service;

import com.roomix.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final AppProperties appProperties;

    /** Répertoire local pour le stockage en mode développement */
    private static final Path LOCAL_UPLOADS_DIR = Path.of("uploads");

    // ─────────────────────────────────────────────────────────────────────────
    // Upload
    // ─────────────────────────────────────────────────────────────────────────

    public String uploadImage(MultipartFile file, UUID userId) {
        validateImage(file);

        String extension = getExtension(file.getOriginalFilename());
        String key = String.format("users/%s/images/%s.%s", userId, UUID.randomUUID(), extension);

        if (isS3Configured()) {
            uploadToS3(file, key);
        } else {
            saveLocally(file, key);
        }

        return key;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // URL publique
    // ─────────────────────────────────────────────────────────────────────────

    public String getPublicUrl(String key) {
        String endpoint = appProperties.getStorage().getEndpoint();
        String bucket   = appProperties.getStorage().getBucket();

        if (endpoint != null && !endpoint.isBlank()) {
            // URL publique Supabase : https://project.supabase.co/storage/v1/object/public/bucket/key
            // (l'endpoint S3 est .../storage/v1/s3 — on remplace par .../storage/v1/object/public)
            String baseUrl = endpoint.replaceAll("/s3$", "");
            return String.format("%s/object/public/%s/%s", baseUrl, bucket, key);
        }

        // Mode développement local : servie par ce backend
        String serverBase = appProperties.getServerBaseUrl();
        return String.format("%s/api/v1/storage/%s", serverBase, key);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sauvegarde d'une image générée (bytes bruts → stockage + URL publique)
    // Utilisé par les APIs IA qui retournent du b64_json (ex: OpenAI gpt-image-1)
    // ─────────────────────────────────────────────────────────────────────────

    public String saveGeneratedImage(byte[] bytes, UUID userId) {
        String key = String.format("users/%s/generated/%s.jpg", userId, UUID.randomUUID());

        if (isS3Configured()) {
            try {
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(appProperties.getStorage().getBucket())
                        .key(key)
                        .contentType("image/jpeg")
                        .contentLength((long) bytes.length)
                        .build();
                s3Client.putObject(request, RequestBody.fromBytes(bytes));
                log.info("Image générée uploadée sur S3: {}", key);
            } catch (Exception e) {
                throw new RuntimeException("Erreur upload S3 image générée: " + e.getMessage(), e);
            }
        } else {
            try {
                Path filePath = LOCAL_UPLOADS_DIR.resolve(key);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, bytes);
                log.info("Image générée sauvegardée localement: {}", filePath.toAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Erreur sauvegarde locale image générée: " + e.getMessage(), e);
            }
        }

        return getPublicUrl(key);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lecture bytes (pour passer l'image en base64 aux APIs IA)
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] getImageBytes(String key) {
        // 1. Essai lecture locale (mode dev)
        Path localPath = LOCAL_UPLOADS_DIR.resolve(key);
        if (Files.exists(localPath)) {
            try {
                return Files.readAllBytes(localPath);
            } catch (IOException e) {
                log.warn("Impossible de lire l'image locale {}: {}", key, e.getMessage());
            }
        }

        // 2. Mode production S3 — télécharge via l'URL publique Supabase
        if (isS3Configured()) {
            String publicUrl = getPublicUrl(key);
            try {
                java.net.URL url = new java.net.URI(publicUrl).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(30_000);
                conn.setRequestProperty("User-Agent", "RoomixBackend/1.0");
                try (java.io.InputStream in = conn.getInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    log.info("Image téléchargée depuis S3 public URL: {} ({} bytes)", publicUrl, bytes.length);
                    return bytes;
                }
            } catch (Exception e) {
                log.warn("Impossible de télécharger l'image depuis {}: {}", publicUrl, e.getMessage());
            }
        }

        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Suppression
    // ─────────────────────────────────────────────────────────────────────────

    public void deleteImage(String key) {
        if (isS3Configured()) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(appProperties.getStorage().getBucket())
                        .key(key)
                        .build());
                log.info("Image supprimée de S3: {}", key);
            } catch (Exception e) {
                log.warn("Impossible de supprimer l'image S3 {}: {}", key, e.getMessage());
            }
        } else {
            try {
                Files.deleteIfExists(LOCAL_UPLOADS_DIR.resolve(key));
                log.info("Image locale supprimée: {}", key);
            } catch (IOException e) {
                log.warn("Impossible de supprimer l'image locale {}: {}", key, e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Implémentations internes
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isS3Configured() {
        String key = appProperties.getStorage().getAccessKey();
        return key != null && !key.isBlank();
    }

    private void uploadToS3(MultipartFile file, String key) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(appProperties.getStorage().getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Image uploadée sur S3: {}", key);
        } catch (IOException e) {
            throw new RuntimeException("Erreur upload S3: " + e.getMessage(), e);
        }
    }

    private void saveLocally(MultipartFile file, String key) {
        try {
            Path filePath = LOCAL_UPLOADS_DIR.resolve(key);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, file.getBytes());
            log.info("Image sauvegardée localement: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Erreur sauvegarde locale: " + e.getMessage(), e);
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
