package com.roomix.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Quota quota = new Quota();
    private Ai ai = new Ai();
    private Storage storage = new Storage();

    @Data
    public static class Quota {
        private int freeDailyLimit = 3;
        private int premiumDailyLimit = -1;
    }

    @Data
    public static class Ai {
        private OpenAi openai = new OpenAi();
        private Replicate replicate = new Replicate();

        @Data
        public static class OpenAi {
            private String apiKey;
            private String baseUrl;
            private String visionModel;
            private String imageModel;
        }

        @Data
        public static class Replicate {
            private String apiKey;
            private String baseUrl;
            private String sdxlVersion;
            private String fluxVersion;
        }
    }

    @Data
    public static class Storage {
        private String provider;
        private String bucket;
        private String region;
        private String accessKey;
        private String secretKey;
        private String endpoint;
    }
}
