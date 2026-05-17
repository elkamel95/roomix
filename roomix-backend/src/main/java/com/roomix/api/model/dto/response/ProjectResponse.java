package com.roomix.api.model.dto.response;

import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProjectResponse {
    private UUID id;
    private String name;
    private String originalImageUrl;
    private ProjectStatus status;
    private DecorationStyle style;
    private BigDecimal budget;
    private LocalDateTime createdAt;
    private GenerationResponse generation;
}
