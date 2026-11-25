package com.wallet.user.domain;

import com.wallet.shared.domain.DomainException;

public class InvalidUserException extends DomainException {

    public InvalidUserException(String message) {
        super(message);
    }
}
