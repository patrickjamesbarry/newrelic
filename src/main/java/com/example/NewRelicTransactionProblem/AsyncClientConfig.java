package com.example.NewRelicTransactionProblem;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.cookie.IgnoreCookieSpecFactory;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

import java.io.IOException;

import static org.apache.hc.core5.http2.HttpVersionPolicy.FORCE_HTTP_1;
import static org.apache.hc.core5.util.Timeout.ofMinutes;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

@Configuration
public class AsyncClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncClientConfig.class);

    private static final int MAX_CONNECT_TIMEOUT_IN_SEC = 5;

    // Set to match server.tomcat.max-connections, which defaults to 8192.
    // This ensures that an execution request could never have to wait for a client.
    private static final int MAX_CONNECTIONS = 8192;

    @Bean
    @Primary
    public CloseableHttpAsyncClient asyncExternalClient(PoolingAsyncClientConnectionManager connectionManager) {
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setIoThreadCount(1).build();
        LOGGER.info("Async thread pool prefixed with '{}' will use max {} threads", "async-worker", ioReactorConfig.getIoThreadCount());
        var client = asyncClientBuilder("async-worker", ioReactorConfig, connectionManager).build();
        client.start();

        return client;
    }

    protected HttpAsyncClientBuilder asyncClientBuilder(String threadPrefix, IOReactorConfig ioReactorConfig, PoolingAsyncClientConnectionManager connectionManager) {
        return HttpAsyncClients.custom()
                .disableAuthCaching()
                .disableCookieManagement()
                .setDefaultCookieSpecRegistry(name -> new IgnoreCookieSpecFactory())
                .setIOReactorConfig(ioReactorConfig)
                .setIoReactorExceptionCallback((exception) -> LOGGER.error("Unhandled exception in IO Reactor!", exception))
                .setConnectionManager(connectionManager)
                .setVersionPolicy(FORCE_HTTP_1)
                .evictIdleConnections(TimeValue.ofMinutes(5))

                .addExecInterceptorLast("mdcRestore", new AsyncExecChainHandler() {  //The names of the 3 provided exec interceptors are: REDIRECT, PROTOCOL, RETRY
                    @Trace(async = true)
                    @Override
                    public void execute(HttpRequest request, AsyncEntityProducer entityProducer, AsyncExecChain.Scope scope, AsyncExecChain chain, AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
                        LOGGER.info("Interceptor second");
                        if (scope.clientContext instanceof RequestContext context) {
                            try (NewRelicToken token = context.createToken()) {
                                NewRelic.addCustomParameter("Second interceptor", true);
                                chain.proceed(request, entityProducer, scope, asyncExecCallback);
                            }
                        }
                    }
                })

                .addExecInterceptorBefore("mdcRestore", "FIRST_ONE", new AsyncExecChainHandler() {  //The names of the 3 provided exec interceptors are: REDIRECT, PROTOCOL, RETRY
                    @Trace(async = true)
                    @Override
                    public void execute(HttpRequest request, AsyncEntityProducer entityProducer, AsyncExecChain.Scope scope, AsyncExecChain chain, AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
                        if (scope.clientContext instanceof RequestContext context) {
                            try (NewRelicToken token = context.createToken()) {
                                LOGGER.info("Interceptor first");
                                NewRelic.addCustomParameter("First interceptor", true);
                                chain.proceed(request, entityProducer, scope, asyncExecCallback);
                            }
                        }
                    }
                })

                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(MAX_CONNECT_TIMEOUT_IN_SEC))
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .build())
                .setThreadFactory(new DefaultThreadFactory(threadPrefix)); //Applies to IO reactor threads
    }

    @Bean
    @Scope(SCOPE_PROTOTYPE)
    public PoolingAsyncClientConnectionManager connectionManager() {
        return PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(MAX_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS)
                .setConnectionTimeToLive(ofMinutes(5L))
                .build();
    }
}
