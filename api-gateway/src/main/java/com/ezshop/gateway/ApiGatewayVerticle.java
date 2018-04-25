package com.ezshop.gateway;

import com.ezshop.common.BaseHttpMicroServicesVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ezshop.common.ErrorCodes.SYSTEM_ERROR_CODE;
import static com.ezshop.common.HttpResponseCodes.SC_BAD_REQUEST;

/**
 * The verticle for application gateway
 *
 * @author Gary Cheng
 */
public class ApiGatewayVerticle extends BaseHttpMicroServicesVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ApiGatewayVerticle.class);
    private static final String PREFIX_API = "/api/";
    private static final String KEY_HTTP_SERVER = "httpServer";

    private static final String ERROR_EMPTY_SERVICE_NAME = "Empty service name";

    @Override
    public void start(Future<Void> startFuture) {
        super.start();
        JsonObject httpConfig = this.config().getJsonObject(KEY_HTTP_SERVER);
        Router router = Router.router(vertx);
        this.enableCorsSupport(router);
        ServiceDiscoveryRestEndpoint.create(router.getDelegate(), discovery.getDelegate());
        this.configureRouter(router);
        this.createHttpServer(httpConfig, router)
                .subscribe(s -> startFuture.complete(), startFuture::fail);
    }

    private void configureRouter(Router router) {
        router.route().handler(BodyHandler.create());
        router.route(PREFIX_API + "*").handler(this::apiHandler);
        router.route("/*").handler(StaticHandler.create());
    }

    private void apiHandler(RoutingContext context) {
        String path = context.request().uri();
        logger.debug("service path:{}", path);
        String serviceName = null;
        if (path.length() >= PREFIX_API.length()) {
            serviceName = path.substring(PREFIX_API.length()).split("/")[0];
        }
        logger.debug("Service Name:{}", serviceName);
        if (null == serviceName || serviceName.trim().equalsIgnoreCase("")) {
            this.restErrorHandler(context, SC_BAD_REQUEST, SYSTEM_ERROR_CODE, ERROR_EMPTY_SERVICE_NAME);
        } else {
            String serviceUri = path.substring(PREFIX_API.length() + serviceName.length());
            if (serviceUri.trim().length() == 0) {
                serviceUri = "/";
            }
            logger.debug("Service uri:{}", serviceUri);
            this.dispatchRequest(context, serviceName, serviceUri, error -> this.restErrorHandler(context, error));
        }
    }
}