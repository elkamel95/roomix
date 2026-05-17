package com.roomix.api.model.dto.response;

import com.roomix.api.model.enums.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GenerationResponse {
    private UUID id;
    private String resultImageUrl;
    private Integer processingTimeMs;
    private LocalDateTime createdAt;
    private ProjectStatus status;
    private Integer progress;
    private String errorMessage;
}
