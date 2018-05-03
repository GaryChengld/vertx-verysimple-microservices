package com.ezshop.product;

import com.ezshop.product.http.ProductHttpVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ezshop.common.ConfigKeys.KEY_DATABASE;
import static com.ezshop.common.ConfigKeys.KEY_SERVICE_ADDRESS;

/**
 * The Main Verticle of Product Micro Services
 *
 * @author Gary Cheng
 */
public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        logger.debug("Starting main verticle of product micro services");
        this.bindProductService();
        vertx.rxDeployVerticle(ProductHttpVerticle.class.getName(), new DeploymentOptions().setConfig(this.config()))
                .subscribe(id -> startFuture.complete(), startFuture::fail);
    }

    private void bindProductService() {
        JDBCClient jdbc = JDBCClient.createShared(vertx, config().getJsonObject(KEY_DATABASE));
        new ServiceBinder(vertx.getDelegate())
                .setAddress(config().getString(KEY_SERVICE_ADDRESS))
                .register(ProductService.class, ProductService.create(jdbc));
    }
}