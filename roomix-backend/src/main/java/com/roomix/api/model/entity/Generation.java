package com.roomix.api.model.entity;

import com.roomix.api.model.enums.AiModel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "generations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Generation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "result_image_url")
    private String resultImageUrl;

    @Column(name = "result_image_key")
    private String resultImageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "negative_prompt", columnDefinition = "text")
    private String negativePrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AiModel model = AiModel.SDXL;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "cost_usd", precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "replicate_prediction_id")
    private String replicatePredictionId;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "generation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Product> products;
}
