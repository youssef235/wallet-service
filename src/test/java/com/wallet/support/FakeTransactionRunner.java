package com.wallet.support;

import com.wallet.shared.application.TransactionRunner;
import java.util.function.Supplier;

/** Runs work directly, without a real transaction. For pure unit tests. */
public class FakeTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
