package com.wallet.user.domain;

import com.wallet.shared.domain.DomainException;

public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
