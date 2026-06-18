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
