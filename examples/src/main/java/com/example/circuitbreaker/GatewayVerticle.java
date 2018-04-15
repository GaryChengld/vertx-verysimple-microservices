package com.example.circuitbreaker;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(GatewayVerticle.class);
    private static final int PORT = 8080;
    private CircuitBreaker circuitBreaker;

    public static void main(String[] args) {
        Vertx.vertx().rxDeployVerticle(GatewayVerticle.class.getName())
                .subscribe(id -> logger.debug("GatewayVerticle deployed successfully"));
    }

    @Override
    public void start(io.vertx.core.Future<Void> startFuture) {
        logger.debug("Starting GatewayVerticle");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route("/*").handler(this::requestHandler);
        CircuitBreakerOptions options = new CircuitBreakerOptions()
                .setMaxFailures(5)
                .setTimeout(5000)
                .setResetTimeout(30000)
                .setFallbackOnFailure(true);
        this.circuitBreaker = CircuitBreaker.create("circuit-breaker", vertx, options)
                .openHandler(v -> logger.debug("Circuit opened"))
                .halfOpenHandler(v -> logger.debug("Circuit half opened"))
                .closeHandler(v -> logger.debug("Circuit closed"));
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .rxListen(PORT)
                .subscribe(s -> {
                    logger.debug("Gateway HTTP server started on port 8080");
                    startFuture.complete();
                }, startFuture::fail);
    }

    private void requestHandler(RoutingContext context) {
        logger.debug("Received http request");
        circuitBreaker.rxExecuteCommandWithFallback(this::invokeService, this::fallbackHandler)
                .subscribe(json -> context.response()
                        .putHeader("content-type", "application/json")
                        .end(json.encodePrettily()));
    }

    private void invokeService(Future<JsonObject> future) {
        logger.debug("Sending request to service server");
        HttpClientRequest clientRequest = vertx.createHttpClient().get(8081, "localhost", "/");
        clientRequest.toFlowable()
                .flatMap(res -> res.toFlowable()).subscribe(buffer -> future.complete(buffer.toJsonObject()), future::fail);
        clientRequest.end();
    }

    private JsonObject fallbackHandler(Throwable error) {
        logger.debug("Handle fallback");
        Throwable rootCause = error;
        while (null != rootCause.getCause()) {
            rootCause = rootCause.getCause();
        }
        logger.debug(rootCause.getClass().getName());
        return new JsonObject().put("error", "Service is not available, reason:" + error.getMessage());
    }

}