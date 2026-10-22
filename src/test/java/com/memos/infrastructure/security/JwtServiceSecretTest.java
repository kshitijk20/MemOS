package com.memos.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtServiceSecretTest {

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> JwtService.validatedSecret(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");
        assertThatThrownBy(() -> JwtService.validatedSecret(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTheSampleSecretThatUsedToBeTheDefault() {
        assertThatThrownBy(() -> JwtService.validatedSecret(
                "please-change-this-secret-in-production-it-must-be-at-least-256-bits-long"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sample");
    }

    @Test
    void rejectsShortSecrets() {
        assertThatThrownBy(() -> JwtService.validatedSecret("too-short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least");
    }

    @Test
    void acceptsAGeneratedSecret() {
        String secret = "q3Jx9mVt2LwP8sKd4Hn7Rb1Yc6Zf0Ae5Gu3Ti9Ok2Ml8Nj4Pw7Qs1Vx6Uy0Bz5Cr";
        assertThat(JwtService.validatedSecret(secret)).isEqualTo(secret);
    }
}
