package com.example.microservices.com;

import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
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

    protected void dispatchRequest(RoutingContext context, String serviceName, Handler<? super Throwable> errorHandler) {
        logger.debug("Dispatch Http Request {} to service {}", context.request().uri(), serviceName);
        this.getCircuitBreaker(serviceName).<Void>rxExecuteCommand(
                future -> this.dispatchRequestHandler(context, serviceName, future))
                .subscribe(v -> logger.debug("dispatch request completed"), errorHandler::handle);
    }

    protected void dispatchRequestHandler(RoutingContext context, String serviceName, Future<Void> future) {
        this.getWebEndPoint(serviceName)
                .subscribe(webClient -> invokeDispatchHttpRequest(context, webClient, future),
                        throwable -> future.fail("Service [" + serviceName + "] not published"));
    }

    private void invokeDispatchHttpRequest(RoutingContext context, WebClient webClient, Future<Void> future) {
        logger.debug("invokeDispatchHttpRequest, uri:{}", context.request().uri());
        HttpRequest<Buffer> httpRequest = webClient.request(context.request().method(), context.request().uri());
        context.request().headers().getDelegate().forEach(header -> httpRequest.putHeader(header.getKey(), header.getValue()));
        if (context.user() != null) {
            httpRequest.putHeader("user-principal", context.user().principal().encode());
        }
        Single<HttpResponse<Buffer>> result;
        if (null != context.request().formAttributes() && !context.request().formAttributes().isEmpty()) {
            result = httpRequest.rxSendForm(context.request().formAttributes());
        } else if (null != context.getBody()) {
            result = httpRequest.rxSendBuffer(context.getBody());
        } else {
            result = httpRequest.rxSend();
        }
        result.subscribe(response -> {
            if (!context.response().ended()) {
                context.response().setStatusCode(response.statusCode());
                response.headers().getDelegate().forEach(header -> context.response().putHeader(header.getKey(), header.getValue()));
                context.response().end(response.body());
            }
            future.complete();
        }, future::fail);
    }
}