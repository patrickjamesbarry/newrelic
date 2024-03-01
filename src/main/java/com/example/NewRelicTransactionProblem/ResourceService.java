package com.example.NewRelicTransactionProblem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@Component
public class ResourceService {
    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final CloseableHttpAsyncClient client;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    public ResourceService(CloseableHttpAsyncClient client) {
        this.client = client;
    }

    public CompletableFuture<Message<HttpResponse, byte[]>> sendRequest(RequestContext context) throws JsonProcessingException, ExecutionException, InterruptedException {


        ArrayList<String> jobs = new ArrayList<>();
        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        for(int i=0; i<10; i++) {
            CompletableFuture<Void> asyncFuture = CompletableFuture.runAsync(() -> {
                context.getToken().link();

                log.info("doing stuff on a separate thread: {}", Thread.currentThread().getName());

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                NewRelic.addCustomParameter("Job: " + Thread.currentThread().getName(), true);

                log.info("did stuff on a separate thread: {}", Thread.currentThread().getName());
                jobs.add(Thread.currentThread().getName());
            });
            futures.add(asyncFuture);
        }

        final AsyncRequestProducer requestProducer = new BasicRequestProducer(Method.POST, URI.create("https://httpbin.org/post"), AsyncEntityProducers.create(MAPPER.writeValueAsString(Map.of("name1", "value1", "name2", "value2")), ContentType.APPLICATION_JSON));

        CompletableFuture<Message<HttpResponse, byte[]>> completableFuture = new CompletableFuture<>();
        client.execute(requestProducer, new BasicResponseConsumer<>(new AsyncResponseEntityConsumer(context)), null, context, new ClientCallback(completableFuture, context));

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        String jobString= "";
        for(String entry : jobs){
            jobString = jobString + "," + entry;
        }
        log.info("Jobs Done: {}", jobString);

        return completableFuture;
    }

    private record ClientCallback(CompletableFuture<Message<HttpResponse, byte[]>> future, RequestContext context) implements FutureCallback<Message<HttpResponse, byte[]>> {

        @Trace(async = true)
            @Override
            public void completed(Message<HttpResponse, byte[]> result) {
                context.getToken().link();
                try {
                    JsonNode responseData = MAPPER.readTree(result.getBody());
                    log.info("{}", responseData);
                    future.complete(result);
                } catch (IOException ex) {
                    log.error("Error processing jSON content: {}", ex.getMessage());
                    future.completeExceptionally(ex);
                }
            }

            @Trace(async = true)
            @Override
            public void failed(Exception e) {
                context.getToken().link();
                log.info("In failed");
                future.completeExceptionally(e);
            }

            @Trace(async = true)
            @Override
            public void cancelled() {
                context.getToken().link();
                try {
                    log.warn("Request was canceled");
                    future.cancel(true);
                } catch (CompletionException | CancellationException ce) {
                    future.completeExceptionally(ce);
                }
            }
        }
}
