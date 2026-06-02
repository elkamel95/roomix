package com.roomix.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final AppProperties appProperties;

    @Bean
    public S3Client s3Client() {
        AppProperties.Storage storage = appProperties.getStorage();
        String accessKey = storage.getAccessKey();
        String secretKey = storage.getSecretKey();

        // Si les clés ne sont pas configurées, on retourne un client stub
        if (accessKey == null || accessKey.isBlank()) {
            log.warn("⚠️  Storage S3/Supabase non configuré (AWS_ACCESS_KEY vide). Upload d'images désactivé.");
            // Retourne un client minimal avec des credentials fictifs pour éviter l'erreur de démarrage
            return S3Client.builder()
                    .region(Region.EU_WEST_3)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("disabled", "disabled")
                    ))
                    .build();
        }

        // Supabase requiert la région "auto" — on utilise us-east-1 comme proxy
        // et on désactive la validation de région pour compatibilité
        String region = storage.getRegion() != null && !storage.getRegion().isBlank()
                ? storage.getRegion() : "us-east-1";

        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .checksumValidationEnabled(false)
                        .build());

        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()));
        }

        return builder.build();
    }
}
