package com.wallet.shared.domain;

public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
