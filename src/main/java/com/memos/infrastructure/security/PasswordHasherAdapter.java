package com.memos.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.memos.application.auth.PasswordHasherPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }
}
