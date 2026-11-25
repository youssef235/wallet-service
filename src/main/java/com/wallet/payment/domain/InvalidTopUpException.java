package com.wallet.payment.domain;

import com.wallet.shared.domain.DomainException;

public class InvalidTopUpException extends DomainException {

    public InvalidTopUpException(String message) {
        super(message);
    }
}
