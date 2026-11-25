package com.wallet.user.domain;

import com.wallet.shared.domain.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {

    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email);
    }
}
