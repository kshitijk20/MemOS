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
