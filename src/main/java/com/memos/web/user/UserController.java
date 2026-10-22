package com.memos.web.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memos.application.user.UpdateProfileCommand;
import com.memos.application.user.UserProfileService;
import com.memos.domain.user.User;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.web.auth.UserSummaryResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<UserSummaryResponse> getProfile(@AuthenticationPrincipal CustomUserPrincipal principal) {
        User user = userProfileService.getProfile(principal.getId());
        return ResponseEntity.ok(UserSummaryResponse.from(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserSummaryResponse> updateProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        User updated = userProfileService.updateProfile(
                principal.getId(),
                new UpdateProfileCommand(
                        request.fullName(),
                        request.email(),
                        request.currentPassword(),
                        request.newPassword()));

        return ResponseEntity.ok(UserSummaryResponse.from(updated));
    }
}
