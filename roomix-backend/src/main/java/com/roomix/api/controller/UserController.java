package com.roomix.api.controller;

import com.roomix.api.model.dto.response.UserResponse;
import com.roomix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

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
                        .createdAt(user.getCreatedAt())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me/quota")
    public ResponseEntity<Map<String, Object>> getQuota(@AuthenticationPrincipal UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(user -> {
                    int limit = switch (user.getPlan()) {
                        case FREE -> 3;
                        case PREMIUM, PRO -> -1;
                    };
                    int used = user.getDailyGenerations();
                    int remaining = limit == -1 ? Integer.MAX_VALUE : Math.max(0, limit - used);

                    return ResponseEntity.ok(Map.of(
                            "plan", user.getPlan().name(),
                            "dailyUsed", used,
                            "dailyLimit", limit,
                            "remaining", remaining,
                            "resetsAt", user.getLastGenerationReset().plusDays(1).toString()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
