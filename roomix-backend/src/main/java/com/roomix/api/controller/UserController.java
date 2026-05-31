package com.roomix.api.controller;

import com.roomix.api.model.dto.response.UserResponse;
import com.roomix.api.repository.UserRepository;
import com.roomix.api.service.TokenCostCalculator;
import com.roomix.api.model.enums.AiModel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final TokenCostCalculator tokenCostCalculator;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(user -> ResponseEntity.ok(UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .avatarUrl(user.getAvatarUrl())
                        .plan(user.getPlan())
                        .planExpiry(user.getPlanExpiry())
                        .tokenBalance(user.getTokenBalance())
                        .createdAt(user.getCreatedAt())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me/quota")
    public ResponseEntity<Map<String, Object>> getQuota(@AuthenticationPrincipal UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(user -> {
                    Map<String, Object> quotaMap = new HashMap<>();
                    quotaMap.put("plan",         user.getPlan().name());
                    quotaMap.put("dailyUsed",    user.getDailyGenerations());
                    quotaMap.put("tokenBalance", user.getTokenBalance());
                    quotaMap.put("resetsAt",     user.getLastGenerationReset().plusDays(1).toString());
                    return ResponseEntity.ok(quotaMap);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retourne le coût en tokens d'une génération selon le modèle/taille/qualité.
     * GET /users/me/token-cost?model=CHATGPT&size=1024x1024&quality=medium
     */
    @GetMapping("/me/token-cost")
    public ResponseEntity<Map<String, Object>> getTokenCost(
            @RequestParam(defaultValue = "QWEN")   String model,
            @RequestParam(defaultValue = "auto")   String size,
            @RequestParam(defaultValue = "auto")   String quality) {
        try {
            AiModel aiModel = AiModel.valueOf(model.toUpperCase());
            int cost = tokenCostCalculator.calculateCost(aiModel, size, quality);
            return ResponseEntity.ok(Map.of("cost", cost, "model", model, "size", size, "quality", quality));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
