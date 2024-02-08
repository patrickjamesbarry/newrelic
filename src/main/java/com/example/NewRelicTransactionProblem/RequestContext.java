package com.example.NewRelicTransactionProblem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.StringJoiner;

/**
 * Manages the saving and restoring of context that must be saved and restored across thread
 * dispatching during async processing.
 * <p>
 * Context managed by class:
 * - MDC
 * - NewRelic Token
 * - Action Metric
 */
public class RequestContext extends ThreadContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestContext.class);

    public static final String REQUEST_CONTEXT = "com.inin.integrations.hedwig.context.RequestContext";

    private final RequestType requestType;
    private final Instant startTime;

    public RequestContext(Instant startTime, RequestType requestType) {
        super();
        this.startTime = startTime;
        this.requestType = requestType;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RequestContext.class.getSimpleName() + "[", "]").add(super.toString())
                //using '+' inside a StringJoiner because .add() joins with ',' and we don't want that with key value pairs
                .toString();
    }

    public enum RequestType {
        ASYNC, SYNC, KAFKA_EVENT
    }
}
