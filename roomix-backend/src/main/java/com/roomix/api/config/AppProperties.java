package com.roomix.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Quota quota = new Quota();
    private Ai ai = new Ai();
    private Storage storage = new Storage();
    private Stripe stripe = new Stripe();
    private ProductSearch productSearch = new ProductSearch();

    @Data
    public static class ProductSearch {
        /** Active la recherche en ligne de produits réels via ChatGPT Vision. */
        private boolean enabled = false;
        /** Marques à interroger : IKEA, CONFORAMA. */
        private List<String> brands = List.of("IKEA", "CONFORAMA");
        /** Nombre max de produits récupérés. */
        private int maxResultsPerBrand = 3;
    }

    /** URL externe du serveur — utilisée pour construire les URLs d'images locales */
    private String serverBaseUrl = "http://localhost:8080";

    @Data
    public static class Quota {
        private int freeDailyLimit = 3;
        private int premiumDailyLimit = -1;
    }

    @Data
    public static class Ai {
        private OpenAi openai = new OpenAi();
        private Replicate replicate = new Replicate();
        private Qwen qwen = new Qwen();

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

        @Data
        public static class Qwen {
            private String apiKey;
            private String baseUrl      = "https://dashscope-intl.aliyuncs.com/api/v1";
            private String visionModel  = "qwen-vl-max";
            private String imageModel   = "wan2.7-image-pro";
        }
    }

    @Data
    public static class Stripe {
        private String secretKey      = "";
        private String webhookSecret  = "";
        private String successUrl     = "ROOMIX://payment/success";
        private String cancelUrl      = "ROOMIX://payment/cancel";
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
