package com.wallet.user.application;

/**
 * Hashes refresh token values before storage (SHA-256).
 */
public interface TokenHasher {

    String hash(String tokenValue);
}
