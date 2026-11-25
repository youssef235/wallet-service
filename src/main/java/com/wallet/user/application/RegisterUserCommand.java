package com.wallet.user.application;

public record RegisterUserCommand(String email, String fullName, String rawPassword) {
}
