package com.wallet.payment.domain;

import com.wallet.shared.domain.DomainException;

public class PaymentGatewayException extends DomainException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
