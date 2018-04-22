package com.ezshop.gateway;


import com.ezshop.common.BaseHttpMicroServicesVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The verticle for application gateway
 *
 * @author Gary Cheng
 */
public class ApiGatewayVerticle extends BaseHttpMicroServicesVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ApiGatewayVerticle.class);
    private static final String PREFIX_SERVICE = "/api/";
    private static final String KEY_HTTP_SERVER = "httpServer";
    private static final String KEY_PORT = "port";

    @Override
    public void start(Future<Void> startFuture) {
        super.start();
        JsonObject httpConfig = this.config().getJsonObject(KEY_HTTP_SERVER);
        int port = httpConfig.getInteger(KEY_PORT);
        Router router = Router.router(vertx);
        this.enableCorsSupport(router);
        ServiceDiscoveryRestEndpoint.create(router.getDelegate(), discovery.getDelegate());
        this.configureRouter(router);
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
        server.rxListen(port).subscribe(s -> {
            logger.debug("Gateway HTTP server started on port {}", port);
            startFuture.complete();
        }, startFuture::fail);
    }

    private void configureRouter(Router router) {
        router.route().handler(BodyHandler.create());
        router.route(PREFIX_SERVICE + "*").handler(this::serviceHandler);
        router.route("/*").handler(StaticHandler.create());
        //String regexMatcher = "^(?!" + PREFIX_SERVICE + ").*";
        //router.routeWithRegex(regexMatcher).handler(this::indexHandler);
        //router.route("/").handler(this::indexHandler);
    }

    private void indexHandler(RoutingContext context) {
        logger.debug("Received http request");
        context.response()
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("message", "Welcome to ezShop").encodePrettily());
    }

    private void serviceHandler(RoutingContext context) {
        String path = context.request().uri();
        logger.debug("service path:{}", path);
        String serviceName = path.substring(PREFIX_SERVICE.length()).split("/")[0];
        logger.debug("Service Name:{}", serviceName);
        if (null == serviceName || serviceName.trim().equalsIgnoreCase("")) {
            this.error(context, 500, "Empty service name");
        } else {
            this.dispatchRequest(context, serviceName, error -> this.error(context, error));
        }
    }

    private void error(RoutingContext context, Throwable throwable) {
        this.error(context, 500, throwable.getMessage());
    }

    private void error(RoutingContext context, int statusCode, String errorMessage) {
        if (!context.response().ended()) {
            context.response().setStatusCode(statusCode).putHeader("content-type", "application/json")
                    .end(new JsonObject().put("statusCode", statusCode).put("error", errorMessage).encodePrettily());
        }
    }
}