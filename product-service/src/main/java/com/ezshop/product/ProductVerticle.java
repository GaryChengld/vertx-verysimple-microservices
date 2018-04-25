package com.ezshop.product;

import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.serviceproxy.ServiceBinder;

/**
 * Verticle which publish product service to event bus
 *
 * @author Gary Cheng
 */
public class ProductVerticle extends AbstractVerticle {
    private static final String KEY_DATABASE = "database";
    private static final String SERVICE_ADDRESS = "serviceProxyAddress";

    @Override
    public void start(Future<Void> startFuture) {
        JDBCClient jdbc = JDBCClient.createShared(vertx, config().getJsonObject(KEY_DATABASE));
        new ServiceBinder(vertx.getDelegate())
                .setAddress(config().getString(SERVICE_ADDRESS))
                .register(ProductService.class, ProductService.create(jdbc));
        startFuture.complete();
    }
}
