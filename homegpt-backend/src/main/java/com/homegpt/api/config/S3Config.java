package com.homegpt.api.config;

import com.homegpt.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final AppProperties appProperties;

    @Bean
    public S3Client s3Client() {
        AppProperties.Storage storage = appProperties.getStorage();

        var builder = S3Client.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())
                ));

        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()));
        }

        return builder.build();
    }
}
