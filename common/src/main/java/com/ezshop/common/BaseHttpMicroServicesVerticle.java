package com.ezshop.common;

import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
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

import static com.ezshop.common.ConfigKeys.KEY_PORT;
import static com.ezshop.common.ErrorCodes.SYSTEM_ERROR_CODE;
import static com.ezshop.common.HttpResponseCodes.SC_INTERNAL_SERVER_ERROR;
import static com.ezshop.common.HttpResponseCodes.SC_OK;

/**
 * The abstract verticle which work as HTTP server
 *
 * @author Gary Cheng
 */
public abstract class BaseHttpMicroServicesVerticle extends BaseMicroServicesVerticle {
    private static final Logger logger = LoggerFactory.getLogger(BaseMicroServicesVerticle.class);
    private static final String KEY_ERROR = "error";
    private static final String KEY_ERROR_CODE = "errorCode";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";

    /**
     * Create a HTTP server by given config and router
     *
     * @param config HTTP config
     * @param router the router receives HTTP request
     * @return
     */
    protected Single<HttpServer> createHttpServer(JsonObject config, Router router) {
        int port = config.getInteger(KEY_PORT);
        HttpServer server = vertx.createHttpServer();
        server.requestStream()
                .toFlowable()
                .map(HttpServerRequest::pause)
                .onBackpressureDrop(req -> req.response().setStatusCode(503).end())
                .observeOn(RxHelper.scheduler(vertx.getDelegate()))
                .subscribe(req -> {
                    req.resume();
                    router.accept(req);
                });
        return server.rxListen(port).doAfterSuccess(s -> logger.debug("http server started on port {}", port));
    }

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
     * Write generic json object to HTTP response
     *
     * @param context
     * @param jsonString the json context to be wrote to HTTP response
     */
    protected void restResponseHandler(RoutingContext context, String jsonString) {
        if (!context.response().ended()) {
            context.response().setStatusCode(SC_OK).putHeader("content-type", "application/json")
                    .end(jsonString);
        }
    }

    /**
     * Generate Rest error response by given exception
     *
     * @param context
     * @param throwable
     */
    protected void restErrorHandler(RoutingContext context, Throwable throwable) {
        this.restErrorHandler(context, SC_INTERNAL_SERVER_ERROR, SYSTEM_ERROR_CODE, throwable.getMessage());
    }

    /**
     * Generate Rest error response by given statusCode, errorCode and error message
     *
     * @param context
     * @param statusCode   the HTTP response status code
     * @param errorCode    the error code
     * @param errorMessage the error message
     */
    protected void restErrorHandler(RoutingContext context, int statusCode, String errorCode, String errorMessage) {
        if (!context.response().ended()) {
            JsonObject json = new JsonObject().put(KEY_ERROR, new JsonObject().put(KEY_ERROR_CODE, errorCode).put(KEY_ERROR_MESSAGE, errorMessage));
            context.response().setStatusCode(statusCode).putHeader("content-type", "application/json")
                    .end(json.encodePrettily());
        }
    }

    /**
     * Dispatch a HTTP request to a HttpEndPoint service
     *
     * @param context      Routing context
     * @param serviceName  the name of service
     * @param uri          the uri at HttpEndPoint service
     * @param errorHandler the async error handler
     */
    protected void dispatchRequest(RoutingContext context, String serviceName, String uri, Handler<? super Throwable> errorHandler) {
        logger.debug("Dispatch Http Request {} to {} service", uri, serviceName);
        this.getCircuitBreaker(serviceName).<Void>rxExecuteCommand(
                future -> this.dispatchRequestHandler(context, serviceName, uri, future))
                .subscribe(v -> logger.debug("dispatch request completed"), errorHandler::handle);
    }

    private void dispatchRequestHandler(RoutingContext context, String serviceName, String uri, Future<Void> future) {
        this.getWebEndPoint(serviceName)
                .subscribe(webClient -> invokeDispatchHttpRequest(context, webClient, uri, future),
                        throwable -> future.fail("Service [" + serviceName + "] not published"));
    }

    private void invokeDispatchHttpRequest(RoutingContext context, WebClient webClient, String uri, Future<Void> future) {
        logger.debug("invokeDispatchHttpRequest, uri:{}", uri);
        HttpRequest<Buffer> httpRequest = webClient.request(context.request().method(), uri);
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