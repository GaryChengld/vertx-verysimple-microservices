package com.example.microservices.com;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The abstract verticle which work as HTTP server
 *
 * @author Gary Cheng
 */
public abstract class BaseHttpMicroServicesVerticle extends BaseMicroServicesVerticle {
    private static final Logger logger = LoggerFactory.getLogger(BaseMicroServicesVerticle.class);

    /**
     * Enable CORS
     *
     * @param router
     */
    protected void enableCorsSupport(Router router) {
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("accept");

        CorsHandler corsHandler = CorsHandler.create("*").allowedHeaders(allowedHeaders);
        Arrays.asList(HttpMethod.values()).stream().forEach(method -> corsHandler.allowedMethod(method));
        router.route().handler(corsHandler);
    }

    /**
     * Dispatch a http request to MicroServices http end point
     *
     * @param context
     * @param serviceName
     * @param errorHandler
     */
    protected void dispatchHttpRequest(RoutingContext context, String serviceName, Handler<? super Throwable> errorHandler) {
        logger.debug("Dispatch Http Request {} to service {}", context.request().uri(), serviceName);
        this.getCircuitBreaker(serviceName).rxExecuteCommandWithFallback(
                future -> this.getServiceHttpClient(serviceName)
                        .subscribe(httpClient -> this.executeDispatchHttpRequest(context, httpClient, future),
                                throwable -> future.fail("Service [" + serviceName + "] not published")),
                throwable -> this.circuitFallback(errorHandler, throwable))
                .subscribe(v -> logger.debug("dispatch request completed"));
    }

    private Void circuitFallback(Handler<? super Throwable> errorHandler, Throwable throwable) {
        errorHandler.handle(throwable);
        return null;
    }

    private void executeDispatchHttpRequest(RoutingContext context, HttpClient httpClient, io.vertx.reactivex.core.Future<Void> future) {
        logger.debug("executeDispatchHttpRequest, uri:{}", context.request().uri());
        HttpClientRequest clientRequest = httpClient.request(context.request().method(), context.request().uri());
        clientRequest.toFlowable()
                .flatMap(response -> {
                    if (!context.response().ended()) {
                        context.response().setStatusCode(response.statusCode());
                        response.headers().getDelegate().forEach(header -> context.response().putHeader(header.getKey(), header.getValue()));
                    }
                    return response.toFlowable();
                })
                .subscribe(buffer -> {
                    if (!context.response().ended()) {
                        context.response().end(buffer);
                    }
                    future.complete();
                }, future::fail);
        context.request().headers().getDelegate().forEach(header -> clientRequest.putHeader(header.getKey(), header.getValue()));
        if (context.user() != null) {
            clientRequest.putHeader("user-principal", context.user().principal().encode());
        }
        if (null != context.getBody()) {
            clientRequest.end(context.getBody());
        } else {
            clientRequest.end();
        }
    }
}
