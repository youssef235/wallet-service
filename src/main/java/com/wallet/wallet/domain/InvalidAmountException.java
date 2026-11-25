package com.wallet.wallet.domain;

import com.wallet.shared.domain.DomainException;

public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
