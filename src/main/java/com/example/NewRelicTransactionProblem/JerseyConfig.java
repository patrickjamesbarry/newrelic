package com.example.NewRelicTransactionProblem;

import jakarta.inject.Inject;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JerseyConfig extends ResourceConfig {

    @Inject
    public JerseyConfig(MyApplicationListener myApplicationListener) {
        register(myApplicationListener);
        register(Resource.class);
    }
}
