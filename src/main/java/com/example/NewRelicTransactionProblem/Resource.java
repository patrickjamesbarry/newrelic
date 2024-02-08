package com.example.NewRelicTransactionProblem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.newrelic.api.agent.Trace;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Path("/test")
public class Resource {

    private static final Logger log = LoggerFactory.getLogger(Resource.class);

    @Inject
    private ResourceService service;

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Trace
    public void getTest(@Context ContainerRequestContext request, @Suspended AsyncResponse asyncResponse) throws JsonProcessingException {
        RequestContext requestContext = (RequestContext) request.getProperty(RequestContext.REQUEST_CONTEXT);

        asyncResponse.setTimeout(10, TimeUnit.SECONDS);
        asyncResponse.setTimeoutHandler(response -> {
            response.resume(new TimeoutException("Timeout occurred in timeout handler"));
        });

        service.sendRequest(requestContext)
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        asyncResponse.resume(ex);
                    } else {
                        asyncResponse.resume(Response.status(Response.Status.OK).entity(r.getBody()).build());
                    }
                });
    }
}
