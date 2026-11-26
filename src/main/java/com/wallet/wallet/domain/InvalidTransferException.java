package com.wallet.wallet.domain;

import com.wallet.shared.domain.DomainException;

/**
 * A transfer that cannot be completed. The message is deliberately generic so
 * the endpoint cannot be used to probe which emails are registered.
 */
public class InvalidTransferException extends DomainException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
