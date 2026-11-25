package com.wallet.shared.infrastructure;

import com.wallet.shared.application.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SpringTransactionRunner implements TransactionRunner {

    @Override
    @Transactional(readOnly = true)
    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
