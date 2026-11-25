package com.wallet.user.application;

public record LoginUserCommand(String email, String rawPassword) {
}
