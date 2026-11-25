package com.wallet.user.domain;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Pure domain entity: no framework dependencies.
 */
public class User {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final Long id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final UserRole role;
    private final Instant createdAt;

    private User(Long id, String email, String fullName, String passwordHash, UserRole role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static User register(String email, String fullName, String passwordHash, Instant now) {
        validate(email, fullName);
        return new User(null, normalize(email), fullName.trim(), passwordHash, UserRole.USER, now);
    }

    public static User reconstitute(Long id, String email, String fullName, String passwordHash,
                                    UserRole role, Instant createdAt) {
        return new User(id, email, fullName, passwordHash, role, createdAt);
    }

    private static void validate(String email, String fullName) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidUserException("A valid email address is required");
        }
        if (fullName == null || fullName.trim().length() < 2) {
            throw new InvalidUserException("Full name must be at least 2 characters");
        }
    }

    public static void validateRawPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidUserException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    public Long id() { return id; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public String passwordHash() { return passwordHash; }
    public UserRole role() { return role; }
    public Instant createdAt() { return createdAt; }
}
