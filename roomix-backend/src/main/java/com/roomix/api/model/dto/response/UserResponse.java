package com.roomix.api.model.dto.response;

import com.roomix.api.model.enums.PlanType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private PlanType plan;
    private LocalDateTime planExpiry;
    private LocalDateTime createdAt;
}
