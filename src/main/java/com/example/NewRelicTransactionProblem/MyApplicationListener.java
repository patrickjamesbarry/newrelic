package com.example.NewRelicTransactionProblem;

import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MyApplicationListener implements ApplicationEventListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void onEvent(ApplicationEvent event) {
        switch (event.getType()) {
            case INITIALIZATION_FINISHED ->
                    logger.info("Application was initialized.");
            case DESTROY_FINISHED ->
                    logger.info("Application {} destroyed.", event.getResourceConfig().getApplicationName());
        }
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        // This listener provides us with real time insight while processing requests
        return new MyRequestListener(Instant.now());
    }
}