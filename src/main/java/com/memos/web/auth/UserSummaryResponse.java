package com.memos.web.auth;

import java.util.UUID;

import com.memos.domain.user.Role;
import com.memos.domain.user.User;

public record UserSummaryResponse(UUID id, String email, String fullName, Role role) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
