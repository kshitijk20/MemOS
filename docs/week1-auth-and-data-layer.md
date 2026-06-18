# Week 1 — Auth Flow & Data Layer

Reference dump of the MemOS backend's authentication stack and Flyway schema, for
building sequence diagrams / ER diagrams.

## Contents

1. [Security config](#1-security-config)
2. [JWT auth filter](#2-jwt-auth-filter)
3. [JWT service (token creation/parsing)](#3-jwt-service)
4. [JWT token issuer adapter (refresh-token persistence/rotation)](#4-jwt-token-issuer-adapter)
5. [User details service](#5-user-details-service)
6. [Auth entry point (401 handler)](#6-auth-entry-point)
7. [Auth controller](#7-auth-controller)
8. [Auth application service](#8-auth-application-service)
9. [Supporting types (DTOs, commands, ports, domain User)](#9-supporting-types)
10. [Flyway migrations](#10-flyway-migrations)

---

## 1. Security config

`src/main/java/com/memos/infrastructure/security/SecurityConfig.java`

```java
package com.memos.infrastructure.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Value("${memos.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

**Key points:**
- Stateless sessions (`SessionCreationPolicy.STATELESS`) — every request must carry its own JWT.
- `/api/v1/auth/**`, actuator health/info, and Swagger docs are public; everything else requires auth.
- `JwtAuthFilter` runs before Spring Security's standard `UsernamePasswordAuthenticationFilter`.
- CORS origins come from `memos.cors.allowed-origins` (defaults to `http://localhost:3000` — the frontend dev server).

---

## 2. JWT auth filter

`src/main/java/com/memos/infrastructure/security/JwtAuthFilter.java`

```java
package com.memos.infrastructure.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            String email = jwtService.extractEmail(token);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejecting request with invalid JWT: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
```

**Flow:**
1. No `Authorization: Bearer <token>` header → pass through unauthenticated (the request will be rejected later by `authorizeHttpRequests` + `JwtAuthenticationEntryPoint` if the endpoint requires auth).
2. Header present → extract email (JWT `sub` claim) via `JwtService.extractEmail`.
3. Look up the user via `UserDetailsServiceImpl`, build a `UsernamePasswordAuthenticationToken`, and place it in the `SecurityContextHolder`.
4. Invalid/expired token → swallow the exception, request continues unauthenticated (and will 401 if the endpoint is protected).

---

## 3. JWT service

`src/main/java/com/memos/infrastructure/security/JwtService.java`

```java
package com.memos.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.memos.domain.user.Role;
import com.memos.domain.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Low-level JWT creation/parsing and opaque refresh-token generation. Application-facing
 * orchestration (persisting refresh tokens, rotation, revocation) lives in
 * {@link JwtTokenIssuerAdapter}.
 */
@Component
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(
            @Value("${memos.jwt.secret}") String secret,
            @Value("${memos.jwt.issuer}") String issuer,
            @Value("${memos.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${memos.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).get(CLAIM_USER_ID, String.class));
    }

    public Role extractRole(String token) {
        return Role.valueOf(parseClaims(token).get(CLAIM_ROLE, String.class));
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    /**
     * Generates a cryptographically random opaque refresh-token value (not a JWT).
     */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes an opaque refresh-token value for storage, so the raw token is never persisted.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
```

**Key points:**
- Access tokens are HMAC-signed JWTs (`memos.jwt.secret`, HS-family via `Keys.hmacShaKeyFor`), `sub` = email, plus `userId` and `role` claims.
- Access token TTL: `memos.jwt.access-token-expiration-ms` (default 1 hour).
- Refresh tokens are **opaque random strings** (32 random bytes, base64url) — *not* JWTs. Only their SHA-256 hash is stored in `refresh_tokens.token_hash`, so a leaked DB doesn't expose usable tokens.
- Refresh token TTL: `memos.jwt.refresh-token-expiration-ms` (default 7 days).

---

## 4. JWT token issuer adapter

`src/main/java/com/memos/infrastructure/security/JwtTokenIssuerAdapter.java`

Implements `TokenIssuerPort` — the application layer depends only on this interface (hexagonal architecture).

```java
package com.memos.infrastructure.security;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.application.auth.TokenIssuerPort;
import com.memos.application.auth.TokenPair;
import com.memos.domain.exception.InvalidCredentialsException;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;
import com.memos.infrastructure.persistence.entity.RefreshTokenEntity;
import com.memos.infrastructure.persistence.repository.RefreshTokenJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

    private final JwtService jwtService;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final UserRepositoryPort userRepositoryPort;

    @Override
    @Transactional
    public TokenPair issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateOpaqueToken();

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .userId(user.getId())
                .tokenHash(jwtService.hashToken(refreshToken))
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenJpaRepository.save(entity);

        return new TokenPair(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    @Override
    @Transactional
    public TokenPair refreshTokens(String refreshToken) {
        RefreshTokenEntity entity = refreshTokenJpaRepository.findByTokenHash(jwtService.hashToken(refreshToken))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (entity.isRevoked() || entity.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Refresh token is expired or has been revoked");
        }

        User user = userRepositoryPort.findById(entity.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        entity.setRevoked(true);
        refreshTokenJpaRepository.save(entity);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        refreshTokenJpaRepository.findByTokenHash(jwtService.hashToken(refreshToken))
                .ifPresent(entity -> {
                    entity.setRevoked(true);
                    refreshTokenJpaRepository.save(entity);
                });
    }
}
```

**Key points:**
- `issueTokens` — on register/login, mints a new access JWT + opaque refresh token, persists the refresh token's hash + expiry.
- `refreshTokens` — **rotation**: validates the presented refresh token (not revoked, not expired), marks it revoked, then issues a brand-new pair. A refresh token can only be used once.
- `revokeRefreshToken` — used by `/auth/logout` to invalidate a refresh token early.

---

## 5. User details service

`src/main/java/com/memos/infrastructure/security/UserDetailsServiceImpl.java`

```java
package com.memos.infrastructure.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepositoryPort.findByEmail(email)
                .map(CustomUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
```

Used by `JwtAuthFilter` to turn the JWT's `sub` (email) into a Spring Security `UserDetails`
(wrapped as `CustomUserPrincipal`, which exposes the domain `User`).

---

## 6. Auth entry point

`src/main/java/com/memos/infrastructure/security/JwtAuthenticationEntryPoint.java`

```java
package com.memos.infrastructure.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memos.web.common.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Returns a JSON 401 response (instead of the default redirect/HTML) when an unauthenticated
 * request is made to a protected endpoint.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse error = ApiErrorResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required to access this resource",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), error);
    }
}
```

Wired into `SecurityConfig` via `.exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))` — every 401 returns the same `ApiErrorResponse` shape as other errors.

---

## 7. Auth controller

`src/main/java/com/memos/web/auth/AuthController.java`

```java
package com.memos.web.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memos.application.auth.AuthApplicationService;
import com.memos.application.auth.AuthResult;
import com.memos.application.auth.LoginCommand;
import com.memos.application.auth.RegisterCommand;
import com.memos.application.auth.TokenPair;
import com.memos.domain.user.User;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;

    @PostMapping("/register")
    public ResponseEntity<UserSummaryResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authApplicationService.register(new RegisterCommand(request.email(), request.password(), request.fullName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserSummaryResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authApplicationService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(AuthResponse.from(result.tokens()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair tokens = authApplicationService.refresh(request.refreshToken());
        return ResponseEntity.ok(AuthResponse.from(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authApplicationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
```

### Endpoints

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/v1/auth/register` | `RegisterRequest{email,password,fullName}` | `201` `UserSummaryResponse{id,email,fullName,role}` |
| POST | `/api/v1/auth/login` | `LoginRequest{email,password}` | `200` `AuthResponse{accessToken,refreshToken,tokenType,expiresIn}` |
| POST | `/api/v1/auth/refresh` | `RefreshRequest{refreshToken}` | `200` `AuthResponse{...}` (rotated pair) |
| POST | `/api/v1/auth/logout` | `RefreshRequest{refreshToken}` | `204` No Content |

All four are `permitAll` in `SecurityConfig`.

---

## 8. Auth application service

`src/main/java/com/memos/application/auth/AuthApplicationService.java`

```java
package com.memos.application.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.InvalidCredentialsException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenIssuerPort tokenIssuerPort;

    public User register(RegisterCommand command) {
        if (userRepositoryPort.existsByEmail(command.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = User.builder()
                .email(command.email())
                .passwordHash(passwordHasherPort.hash(command.rawPassword()))
                .fullName(command.fullName())
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return userRepositoryPort.save(user);
    }

    public AuthResult login(LoginCommand command) {
        User user = userRepositoryPort.findByEmail(command.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!user.isEnabled() || !passwordHasherPort.matches(command.rawPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return new AuthResult(user, tokenIssuerPort.issueTokens(user));
    }

    public TokenPair refresh(String refreshToken) {
        return tokenIssuerPort.refreshTokens(refreshToken);
    }

    public void logout(String refreshToken) {
        tokenIssuerPort.revokeRefreshToken(refreshToken);
    }
}
```

`AuthResult`:

```java
package com.memos.application.auth;

import com.memos.domain.user.User;

public record AuthResult(User user, TokenPair tokens) {
}
```

**Notes:**
- `register` — checks email uniqueness, hashes password (BCrypt via `PasswordHasherPort`), saves with `role=USER`, `enabled=true`. Does **not** auto-login (no tokens returned — client calls `/login` next).
- `login` — single error message ("Invalid email or password") for both "user not found" and "wrong password", to avoid leaking which case applies. Also rejects disabled accounts.
- `refresh` / `logout` — thin pass-throughs to `TokenIssuerPort` (see §4).

---

## 9. Supporting types

Small DTOs/commands/ports referenced above, included for completeness.

**Request DTOs** (`src/main/java/com/memos/web/auth/`)

```java
// RegisterRequest.java
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 150) String fullName) {
}

// LoginRequest.java
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}

// RefreshRequest.java
public record RefreshRequest(
        @NotBlank String refreshToken) {
}
```

**Response DTOs**

```java
// UserSummaryResponse.java
public record UserSummaryResponse(UUID id, String email, String fullName, Role role) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}

// AuthResponse.java
public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    public static AuthResponse from(TokenPair tokens) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn());
    }
}
```

**Application-layer commands/ports** (`src/main/java/com/memos/application/auth/`)

```java
// RegisterCommand.java
public record RegisterCommand(String email, String rawPassword, String fullName) {
}

// LoginCommand.java
public record LoginCommand(String email, String rawPassword) {
}

// TokenPair.java
/**
 * @param accessToken  short-lived JWT used to authenticate API requests
 * @param refreshToken opaque, single-use token used to obtain a new {@link TokenPair}
 * @param expiresIn    access token lifetime, in seconds
 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}

// PasswordHasherPort.java — implemented by a BCrypt adapter
public interface PasswordHasherPort {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hashedPassword);
}

// TokenIssuerPort.java — implemented by JwtTokenIssuerAdapter (see §4)
public interface TokenIssuerPort {
    TokenPair issueTokens(User user);
    TokenPair refreshTokens(String refreshToken);
    void revokeRefreshToken(String refreshToken);
}
```

**Domain `User`** (`src/main/java/com/memos/domain/user/User.java`)

```java
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class User {
    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;
}

// Role.java
public enum Role {
    USER,
    ADMIN
}
```

---

## 10. Flyway migrations

`src/main/resources/db/migration/`

### V1 — users

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
```

### V2 — refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
```

### V3 — documents

```sql
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    source_type     VARCHAR(30) NOT NULL,
    source_uri      TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_owner_id ON documents (owner_id);
CREATE INDEX idx_documents_status ON documents (status);
```

### V4 — pgvector + document_chunks

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- Dimension fixed at 768 to match the default embedding provider (Gemini text-embedding-004).
-- Switching embedding providers to one with a different dimensionality requires a new
-- migration to alter this column and re-embedding all existing chunks.
CREATE TABLE document_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(768) NOT NULL,
    token_count     INT NOT NULL DEFAULT 0,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks (document_id);
CREATE INDEX idx_document_chunks_embedding_hnsw ON document_chunks USING hnsw (embedding vector_cosine_ops);
```

### V5 — conversations

```sql
CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(500) NOT NULL,
    provider    VARCHAR(20) NOT NULL,
    model       VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_id ON conversations (user_id);
```

### V6 — messages

```sql
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
CREATE INDEX idx_messages_conversation_id_created_at ON messages (conversation_id, created_at);
```

### V7 — citations

```sql
CREATE TABLE citations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id          UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    document_id         UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    document_chunk_id   UUID NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    source_title        VARCHAR(500) NOT NULL,
    snippet             TEXT NOT NULL,
    score               DOUBLE PRECISION NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_citations_message_id ON citations (message_id);
```

### V8 — additional indexes

```sql
CREATE INDEX idx_documents_owner_id_created_at ON documents (owner_id, created_at DESC);
CREATE INDEX idx_conversations_user_id_updated_at ON conversations (user_id, updated_at DESC);
```

### ER diagram sketch

```
users (1) ──< (N) refresh_tokens
users (1) ──< (N) documents
users (1) ──< (N) conversations

documents (1) ──< (N) document_chunks
conversations (1) ──< (N) messages

messages (1) ──< (N) citations
citations (N) ──> (1) documents
citations (N) ──> (1) document_chunks
```

All FKs are `ON DELETE CASCADE` — deleting a user cascades through their refresh tokens,
documents (→ chunks), and conversations (→ messages → citations).

---

## End-to-end auth flow summary

```
POST /auth/register
  → AuthApplicationService.register
  → hash password (BCrypt), save User (role=USER, enabled=true)
  → 201 UserSummaryResponse

POST /auth/login
  → AuthApplicationService.login
  → verify password
  → JwtTokenIssuerAdapter.issueTokens
       - generateAccessToken (JWT: sub=email, userId, role, 1h expiry)
       - generateOpaqueToken (refresh token, store SHA-256 hash, 7d expiry)
  → 200 AuthResponse {accessToken, refreshToken, tokenType="Bearer", expiresIn}

Authenticated request
  → JwtAuthFilter reads "Authorization: Bearer <accessToken>"
  → JwtService.extractEmail (validates signature + expiry)
  → UserDetailsServiceImpl.loadUserByUsername(email)
  → SecurityContextHolder populated → request proceeds
  → (no/invalid token + protected endpoint) → JwtAuthenticationEntryPoint → 401 ApiErrorResponse

POST /auth/refresh
  → JwtTokenIssuerAdapter.refreshTokens
  → look up refresh_tokens by hash(refreshToken); reject if revoked/expired
  → mark old refresh token revoked, issue a brand-new pair (rotation)
  → 200 AuthResponse {...}

POST /auth/logout
  → JwtTokenIssuerAdapter.revokeRefreshToken
  → mark refresh_tokens row revoked
  → 204 No Content
```
