package com.memos.application.auth;

/**
 * Hashes and verifies user passwords. Implemented by an infrastructure adapter wrapping a
 * concrete algorithm (e.g. BCrypt).
 */
public interface PasswordHasherPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashedPassword);
}
