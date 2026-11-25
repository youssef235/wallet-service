package com.wallet.shared.application;

import java.util.function.Supplier;

/**
 * Lets pure application use cases run inside a database transaction
 * without depending on Spring annotations.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    /** Read-only work: lets the database optimize and never flushes. */
    default <T> T inReadOnlyTransaction(Supplier<T> work) {
        return inTransaction(work);
    }

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
