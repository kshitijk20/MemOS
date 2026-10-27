package com.memos.web.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memos.application.auth.AuthApplicationService;
import com.memos.application.auth.PasswordResetService;
import com.memos.application.auth.AuthResult;
import com.memos.application.auth.LoginCommand;
import com.memos.application.auth.RegisterCommand;
import com.memos.application.auth.SignupService;
import com.memos.application.auth.TokenPair;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final PasswordResetService passwordResetService;
    private final SignupService signupService;

    /**
     * Step 1 of sign-up: emails a verification code. Always answers 202 with the same message, so
     * the endpoint can't be used to find out which emails have accounts.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        signupService.requireRegistrationOpen();
        signupService.startSignup(new RegisterCommand(request.email(), request.password(), request.fullName()));
        return ResponseEntity.accepted().body(Map.of("message",
                "We've sent a verification code to that email. Enter it to finish creating your account."));
    }

    /** Step 2 of sign-up: creates the account from the emailed code and signs the user in. */
    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> verifyRegistration(@Valid @RequestBody VerifySignupRequest request) {
        AuthResult result = signupService.verifySignup(request.email(), request.code());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(result.tokens()));
    }

    /** Public: lets the sign-up page show a "registration closed" notice. */
    @GetMapping("/registration")
    public ResponseEntity<RegistrationPolicy> registration() {
        return ResponseEntity.ok(new RegistrationPolicy(signupService.registrationOpen()));
    }

    public record RegistrationPolicy(boolean open) {}

    /**
     * Emails a reset link if an account exists. Always answers 202 with the same message, so the
     * endpoint can't be used to find out which emails have accounts.
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().body(Map.of("message",
                "If an account exists for that email, we've sent a link to reset the password."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
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
