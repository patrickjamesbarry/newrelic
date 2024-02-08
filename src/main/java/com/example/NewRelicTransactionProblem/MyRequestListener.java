package com.example.NewRelicTransactionProblem;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.example.NewRelicTransactionProblem.RequestContext.REQUEST_CONTEXT;

public class MyRequestListener implements RequestEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(MyRequestListener.class);

    private static final List<String> URI_PATHS_NOT_TO_LOG = List.of("health/check");
    private static final String[] ASYNC_PATH_SEGMENTS = {"/execute", "/test"};

    private final Instant startTime;
    private RequestContext requestContext;

    public MyRequestListener(Instant startTime) {
        this.startTime = startTime;
    }

    @Override
    public void onEvent(final RequestEvent event) {
        switch (event.getType()) {
            case MATCHING_START:
                matchingStartEvent(event);
                break;
            case RESP_FILTERS_FINISHED:
                //Last chance to set NR attributes. Transaction will be closed after this event by NR.
                NewRelic.addCustomParameter("httpStatusCode", event.getContainerResponse().getStatus());
                break;
            case FINISHED:
                finishedEvent(event);
                break;
            default:
                LOG.trace("{} event not implemented", event.getType());
                break;
        }
    }

    @Trace(dispatcher = true)
    public void matchingStartEvent(RequestEvent event) {
        final ContainerRequest request = event.getContainerRequest();
        final UriInfo uriInfo = request.getUriInfo();

        LOG.trace("starting new relic trace");
        this.requestContext = new RequestContext(getStartTime(), RequestContext.RequestType.ASYNC);
        request.setProperty(REQUEST_CONTEXT, requestContext);
        NewRelic.addCustomParameter("org-id", request.getHeaderString("My-Organization-Id"));

        if (shouldLogTrace(uriInfo.getPath()) && LOG.isInfoEnabled()) {
            //No need to trace full path, http verb, etc because it's going to be traced as part of the MDC context
            LOG.info("> Start {}: [{}]", requestContext.getRequestType(), getSafeQueryParams(request));
        }
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void finishedEvent(RequestEvent event) {
        ContainerRequest containerRequest = event.getContainerRequest();
        if (shouldLogTrace(containerRequest.getUriInfo().getPath()) && LOG.isInfoEnabled()) {
            //No need to trace full path, http verb, etc because it's going to be traced as part of the MDC context
            LOG.info("< End: [{}] : [{}ms]", getSafeQueryParams(containerRequest), Duration.between(startTime, Instant.now())
                    .toMillis());
        }

        requestContext.close();
    }

    private boolean shouldLogTrace(String path) {
        return URI_PATHS_NOT_TO_LOG.stream().noneMatch(path::contains);
    }

    private String getSafeQueryParams(ContainerRequestContext request) {
        var queryParameters = request.getUriInfo().getQueryParameters(true);

        // There are no query parameters to potentially redact.
        if (queryParameters == null || queryParameters.isEmpty()) {
            return "";
        }

        return queryParameters.toString();
    }
}
