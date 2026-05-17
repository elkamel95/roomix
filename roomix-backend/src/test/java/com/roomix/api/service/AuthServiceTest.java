package com.roomix.api.service;

import com.roomix.api.exception.EmailAlreadyExistsException;
import com.roomix.api.model.dto.request.LoginRequest;
import com.roomix.api.model.dto.request.RegisterRequest;
import com.roomix.api.model.dto.response.AuthResponse;
import com.roomix.api.model.entity.User;
import com.roomix.api.model.enums.PlanType;
import com.roomix.api.repository.RefreshTokenRepository;
import com.roomix.api.repository.UserRepository;
import com.roomix.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Tests unitaires")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 604800L);
    }

    // ===================== REGISTER =====================

    @Test
    @DisplayName("register — succès avec données valides")
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("fahmi@roomix.ai");
        req.setPassword("password123");
        req.setFirstName("Fahmi");

        when(userRepository.existsByEmail("fahmi@roomix.ai")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(anyString(), any(UUID.class))).thenReturn("access_token");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(900L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(req);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser().getEmail()).isEqualTo("fahmi@roomix.ai");
        assertThat(response.getUser().getPlan()).isEqualTo(PlanType.FREE);

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register — lève EmailAlreadyExistsException si email pris")
    void register_emailAlreadyExists_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@roomix.ai");
        req.setPassword("password123");
        req.setFirstName("Test");

        when(userRepository.existsByEmail("existing@roomix.ai")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("existing@roomix.ai");

        verify(userRepository, never()).save(any());
    }

    // ===================== LOGIN =====================

    @Test
    @DisplayName("login — succès avec credentials valides")
    void login_success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("fahmi@roomix.ai");
        req.setPassword("password123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("fahmi@roomix.ai")
                .firstName("Fahmi")
                .plan(PlanType.FREE)
                .build();

        when(userRepository.findByEmail("fahmi@roomix.ai")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(anyString(), any(UUID.class))).thenReturn("access_token");
        when(jwtTokenProvider.getAccessTokenExpiry()).thenReturn(900L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getUser().getEmail()).isEqualTo("fahmi@roomix.ai");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login — lève exception si mauvais credentials")
    void login_badCredentials_throwsException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("fahmi@roomix.ai");
        req.setPassword("wrong_password");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(any());
    }

    // ===================== LOGOUT =====================

    @Test
    @DisplayName("logout — révoque tous les refresh tokens")
    void logout_revokesAllTokens() {
        User user = User.builder().id(UUID.randomUUID()).email("fahmi@roomix.ai").build();
        when(userRepository.findByEmail("fahmi@roomix.ai")).thenReturn(Optional.of(user));

        authService.logout("fahmi@roomix.ai");

        verify(refreshTokenRepository).revokeAllByUser(user);
    }
}
