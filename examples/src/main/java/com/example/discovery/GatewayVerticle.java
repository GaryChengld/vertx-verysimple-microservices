package com.example.discovery;

import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(GatewayVerticle.class);
    private static final int PORT = 8080;
    private static final String PREFIX_SERVICE = "/service/";
    private ServiceDiscovery discovery;

    public static void main(String[] args) throws InterruptedException {
        Vertx.rxClusteredVertx(new VertxOptions().setClustered(true))
                .flatMap(vertx -> vertx.rxDeployVerticle(GatewayVerticle.class.getName()))
                .subscribe(id -> logger.debug("GatewayVerticle deployed successfully"));
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        logger.debug("Starting GatewayVerticle");
        this.discovery = ServiceDiscovery.create(vertx);
        Router router = Router.router(vertx);
        ServiceDiscoveryRestEndpoint.create(router.getDelegate(), discovery.getDelegate());
        this.configureRouter(router);
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .rxListen(PORT)
                .subscribe(s -> {
                    logger.debug("Gateway HTTP server started on port 8080");
                    startFuture.complete();
                }, startFuture::fail);
    }

    private void configureRouter(Router router) {
        router.route().handler(BodyHandler.create());
        router.route(PREFIX_SERVICE + "*").handler(this::serviceHandler);
        String regexMatcher = "^(?!" + PREFIX_SERVICE + ").*";
        router.routeWithRegex(regexMatcher).handler(this::indexHandler);
    }

    private void indexHandler(RoutingContext context) {
        logger.debug("Received http request");
        context.response()
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("message", "Welcome to gateway").encodePrettily());
    }

    private void serviceHandler(RoutingContext context) {
        String path = context.request().uri();
        logger.debug("path:{}", path);
        String serviceName = path.substring(PREFIX_SERVICE.length()).split("/")[0];
        logger.debug("Service Name:{}", serviceName);
        if (null == serviceName || serviceName.trim().equalsIgnoreCase("")) {
            this.error(context, 500, "Empty service name");
        } else {
            HttpEndpoint.rxGetClient(discovery, new JsonObject().put("name", serviceName))
                    .subscribe(httpClient -> this.dispatchRequest(context, httpClient), error -> this.serviceNotFound(context, serviceName));
        }
    }

    private void dispatchRequest(RoutingContext context, HttpClient client) {
        logger.debug("Dispatch Request, uri:{}", context.request().uri());
        HttpClientRequest clientRequest = client.request(context.request().method(), context.request().uri());
        clientRequest.toFlowable()
                .flatMap(res -> {
                    context.response().setStatusCode(res.statusCode());
                    res.headers().getDelegate().forEach(header -> context.response().putHeader(header.getKey(), header.getValue()));
                    return res.toFlowable();
                })
                .doAfterTerminate(() -> ServiceDiscovery.releaseServiceObject(discovery, client))
                .subscribe(context.response()::end, error -> this.error(context, error));
        context.request().headers().getDelegate().forEach(header -> clientRequest.putHeader(header.getKey(), header.getValue()));
        if (null != context.getBody()) {
            clientRequest.end(context.getBody());
        } else {
            clientRequest.end();
        }
    }

    private void serviceNotFound(RoutingContext context, String serviceName) {
        this.error(context, 404, "Service [" + serviceName + "] not found");
    }

    private void error(RoutingContext context, Throwable throwable) {
        this.error(context, 500, throwable.getMessage());
    }

    private void error(RoutingContext context, int statusCode, String errorMessage) {
        context.response().setStatusCode(statusCode).putHeader("content-type", "application/json")
                .end(new JsonObject().put("statusCode", statusCode).put("error", errorMessage).encodePrettily());
    }
}