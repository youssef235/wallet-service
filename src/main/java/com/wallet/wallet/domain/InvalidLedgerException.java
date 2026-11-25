package com.wallet.wallet.domain;

import com.wallet.shared.domain.DomainException;

public class InvalidLedgerException extends DomainException {

    public InvalidLedgerException(String message) {
        super(message);
    }
}
