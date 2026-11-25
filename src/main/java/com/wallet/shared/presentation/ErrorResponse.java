package com.wallet.shared.presentation;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(String code, String message, List<FieldViolation> violations, Instant timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of(), Instant.now());
    }

    public static ErrorResponse of(String code, String message, List<FieldViolation> violations) {
        return new ErrorResponse(code, message, violations, Instant.now());
    }
}
