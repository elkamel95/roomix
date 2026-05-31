package com.roomix.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomix.api.exception.EmailAlreadyExistsException;
import com.roomix.api.model.dto.request.LoginRequest;
import com.roomix.api.model.dto.request.RegisterRequest;
import com.roomix.api.model.dto.response.AuthResponse;
import com.roomix.api.model.dto.response.UserResponse;
import com.roomix.api.model.enums.PlanType;
import com.roomix.api.security.JwtAuthenticationFilter;
import com.roomix.api.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)   // désactive FilterChainProxy — les tests contrôleur ne testent pas la sécurité
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "app.jwt.secret=test-secret-key-minimum-256-bits-long-for-testing-only",
        "app.jwt.access-token-expiry=900",
        "app.jwt.refresh-token-expiry=604800"
})
@DisplayName("AuthController — Tests intégration Web")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── Dépendances de SecurityConfig (requises par @RequiredArgsConstructor) ──
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean UserDetailsService      userDetailsService;

    // ── Service métier ──────────────────────────────────────────────────────────
    @MockBean AuthService authService;

    // ────────────────────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(String email) {
        UserResponse user = UserResponse.builder()
                .id(UUID.randomUUID())
                .email(email)
                .firstName("Fahmi")
                .plan(PlanType.FREE)
                .build();
        return AuthResponse.builder()
                .accessToken("access_token_123")
                .refreshToken("refresh_token_456")
                .expiresIn(900L)
                .user(user)
                .build();
    }

    @Test
    @DisplayName("POST /auth/register — 201 avec données valides")
    void register_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("fahmi@roomix.ai");
        req.setPassword("password123");
        req.setFirstName("Fahmi");

        when(authService.register(any())).thenReturn(buildAuthResponse("fahmi@roomix.ai"));

        mockMvc.perform(post("/auth/register")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access_token_123"))
                .andExpect(jsonPath("$.user.email").value("fahmi@roomix.ai"))
                .andExpect(jsonPath("$.user.plan").value("FREE"));
    }

    @Test
    @DisplayName("POST /auth/register — 400 si email manquant")
    void register_returns400_missingEmail() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setPassword("password123");
        req.setFirstName("Fahmi");
        // email manquant

        mockMvc.perform(post("/auth/register")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register — 409 si email déjà utilisé")
    void register_returns409_emailExists() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@roomix.ai");
        req.setPassword("password123");
        req.setFirstName("Test");

        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("Email déjà utilisé"));

        mockMvc.perform(post("/auth/register")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/login — 200 avec credentials valides")
    void login_returns200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("fahmi@roomix.ai");
        req.setPassword("password123");

        when(authService.login(any())).thenReturn(buildAuthResponse("fahmi@roomix.ai"));

        mockMvc.perform(post("/auth/login")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @DisplayName("POST /auth/logout — 204 si authentifié")
    @WithMockUser(username = "fahmi@roomix.ai")
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
