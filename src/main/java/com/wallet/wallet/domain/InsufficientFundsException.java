package com.wallet.wallet.domain;

import com.wallet.shared.domain.DomainException;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(long balanceMinor, long requestedMinor) {
        super("Insufficient funds: balance is " + balanceMinor + " piasters but " + requestedMinor + " were requested");
    }
}
