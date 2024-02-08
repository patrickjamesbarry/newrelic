package com.example.NewRelicTransactionProblem;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Transaction;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.Closeable;
import java.util.StringJoiner;

class ThreadContext extends HttpClientContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadContext.class);
    private final Transaction transaction;

    ThreadContext() {
        super();
        transaction = NewRelic.getAgent().getTransaction();
    }

    public NewRelicToken createToken() {
        return new NewRelicToken(transaction);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                // .add("newRelicToken=" + newRelicToken.isActive())
                .toString();
    }

    /**
     * Does not wipeout internal state of this class, rather releases external resources
     */
    public void close() {
        MDC.clear();
    }
}
