package com.wallet.payment.presentation;

import com.wallet.payment.application.StartTopUpResult;

record StartTopUpResponse(Long topUpId, String merchantOrderId, long amountMinor, String currency,
                          String paymentUrl) {

    static StartTopUpResponse from(StartTopUpResult result) {
        return new StartTopUpResponse(result.topUpId(), result.merchantOrderId(), result.amountMinor(),
                result.currency(), result.paymentUrl());
    }
}
