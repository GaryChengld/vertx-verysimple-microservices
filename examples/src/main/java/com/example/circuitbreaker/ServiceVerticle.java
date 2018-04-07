package com.example.circuitbreaker;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ServiceVerticle.class);
    private static final int PORT = 8081;

    public static void main(String[] args) {
        Vertx.vertx().rxDeployVerticle(ServiceVerticle.class.getName())
                .subscribe(id -> logger.debug("ServiceVerticle deployed successfully"));
    }

    @Override
    public void start(Future<Void> startFuture) {
        logger.debug("Starting ServiceVerticle");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route("/*").handler(this::requestHandler);
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .rxListen(PORT)
                .subscribe(s -> {
                    logger.debug("Service HTTP server started on port 8081");
                    startFuture.complete();
                }, startFuture::fail);
    }

    private void requestHandler(RoutingContext context) {
        logger.debug("Received http request");
        context.response()
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("message", "Invoked service successful").encodePrettily());
    }
}