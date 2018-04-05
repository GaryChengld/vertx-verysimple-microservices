package com.example.discovery;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductHttpVerticle extends AbstractVerticle {

    public static final int PORT = 8081;
    private static final Logger logger = LoggerFactory.getLogger(ProductHttpVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        logger.debug("Starting ProductHttpVerticle");
        Router router = Router.router(vertx);
        router.get("/*").handler(this::productServiceHandler);
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .rxListen(PORT)
                .subscribe(s -> {
                    logger.debug("Product HTTP server started on port {}", PORT);
                    startFuture.complete();
                }, error -> startFuture.fail(error));
    }

    private void productServiceHandler(RoutingContext context) {
        logger.debug("Received a product service request");
        context.response()
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("message", "Welcome to product service").encodePrettily());
    }
}