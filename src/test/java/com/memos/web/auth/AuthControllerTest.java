package com.memos.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.memos.application.auth.AuthApplicationService;
import com.memos.application.auth.AuthResult;
import com.memos.application.auth.TokenPair;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.InvalidCredentialsException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.infrastructure.security.JwtService;
import com.memos.infrastructure.security.UserDetailsServiceImpl;

@WebMvcTest(value = AuthController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void registerReturnsCreatedUserSummary() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hashed")
                .fullName("Alice")
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(authApplicationService.register(any())).thenReturn(user);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"super-secret","fullName":"Alice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice"));
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"123","fullName":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturnsConflictForDuplicateEmail() throws Exception {
        when(authApplicationService.register(any())).thenThrow(new DuplicateResourceException("An account with this email already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"super-secret","fullName":"Alice"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginReturnsTokenPair() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hashed")
                .fullName("Alice")
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(authApplicationService.login(any())).thenReturn(new AuthResult(user, new TokenPair("access-token", "refresh-token", 3600L)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"super-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginReturnsUnauthorizedForBadCredentials() throws Exception {
        when(authApplicationService.login(any())).thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReturnsNewTokenPair() throws Exception {
        when(authApplicationService.refresh("old-refresh")).thenReturn(new TokenPair("new-access", "new-refresh", 3600L));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isNoContent());
    }
}
