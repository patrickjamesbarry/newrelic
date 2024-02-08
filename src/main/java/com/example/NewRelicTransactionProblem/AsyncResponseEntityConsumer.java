package com.example.NewRelicTransactionProblem;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

/**
 * This guy will count the bytes as he consumes them. But don't give him too much to eat, or he will
 * throw an IOException. Make him wait too long on his byte? Another possible IOException thrown.
 */
public class AsyncResponseEntityConsumer extends BasicAsyncEntityConsumer {

    private Instant readStartTime;
    private final RequestContext requestContext;
    private int totalLength = 0;

    public AsyncResponseEntityConsumer(RequestContext requestContext) {
        super();
        this.requestContext = requestContext;
    }

    @Override
    protected final void streamStart(final ContentType contentType) throws HttpException, IOException {
        if (readStartTime == null) {
            readStartTime = Instant.now();
        }
    }

    @Override
    @Trace(async = true, metricName = "EntityConsumer - Consuming bytes")
    protected void data(ByteBuffer src, boolean endOfStream) throws IOException {
        try (NewRelicToken token = requestContext.createToken()) {
            if (src == null) {
                return;
            }
            long durationMs = Duration.between(readStartTime, Instant.now()).toMillis();

            if (durationMs > 500) {
                NewRelic.addCustomParameter("ResponseBufferingTimeMs", durationMs);
                throw new RuntimeException("Transmitting too slow");
            }
            totalLength += src.remaining();

            if (totalLength > 1000) {
                NewRelic.addCustomParameter("ResponseBufferingTimeMs", durationMs);
                NewRelic.addCustomParameter("ResponseSizeBytes", totalLength);
                NewRelic.addCustomParameter("TooBigResponse", "true");
                throw new RuntimeException("Response too big");
            }

            if (endOfStream) {
                NewRelic.addCustomParameter("ResponseBufferingTimeMs", durationMs);
                NewRelic.addCustomParameter("ResponseSizeBytes", totalLength);
            }

            super.data(src, endOfStream);
        }
    }
}