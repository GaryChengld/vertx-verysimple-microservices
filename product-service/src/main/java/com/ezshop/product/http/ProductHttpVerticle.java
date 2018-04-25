package com.ezshop.product.http;

import com.ezshop.common.BaseHttpMicroServicesVerticle;
import com.ezshop.product.reactivex.ProductService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verticle of product HTTP server
 *
 * @author Gary Cheng
 */
public class ProductHttpVerticle extends BaseHttpMicroServicesVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ProductHttpVerticle.class);

    private static final String URI_ALL_CATEGORIES = "/categories";

    private static final String SERVICE_ADDRESS = "serviceProxyAddress";
    private static final String KEY_HTTP_SERVER = "httpServer";
    private ProductService productService;

    @Override
    public void start(Future<Void> startFuture) {
        super.start();
        logger.debug("Starting Product HTTP Server");
        this.productService = com.ezshop.product.ProductService.createProxy(vertx, this.config().getString(SERVICE_ADDRESS));
        JsonObject httpConfig = this.config().getJsonObject(KEY_HTTP_SERVER);
        Router router = Router.router(vertx);
        this.configureRouter(router);
        this.createHttpServer(httpConfig, router)
                .flatMap(httpServer -> this.publishHttpEndPoint("product", httpConfig))
                .subscribe(r -> startFuture.complete(), startFuture::fail);
    }

    private void configureRouter(Router router) {
        this.enableCorsSupport(router);
        router.get(URI_ALL_CATEGORIES).handler(this::getAllCategories);
    }

    private void getAllCategories(RoutingContext context) {
        productService.rxGetAllCategories().subscribe(
                jsonArray -> this.restResponseHandler(context, jsonArray.encodePrettily()),
                error -> this.restErrorHandler(context, error));
    }
}