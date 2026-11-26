package com.wallet.shared.domain;

/** A caller hit a temporary throttle, e.g. too many failed login attempts. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
