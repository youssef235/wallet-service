package com.wallet.shared.application;

import java.util.function.Supplier;

/**
 * Lets pure application use cases run inside a database transaction
 * without depending on Spring annotations.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
