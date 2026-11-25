package com.wallet.wallet.domain;

import com.wallet.shared.domain.DomainException;

public class SelfTransferException extends DomainException {

    public SelfTransferException() {
        super("Cannot transfer to your own wallet");
    }
}
