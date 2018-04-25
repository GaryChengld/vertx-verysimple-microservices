package com.ezshop.product;

import com.ezshop.product.http.ProductHttpVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Main Verticle of Product Micro Services
 *
 * @author Gary Cheng
 */
public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ProductVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        logger.debug("Starting main verticle of product micro services");
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(this.config());
        vertx.rxDeployVerticle(ProductVerticle.class.getName(), deploymentOptions)
                .flatMap(id -> vertx.rxDeployVerticle(ProductHttpVerticle.class.getName(), deploymentOptions))
                .subscribe(id -> {
                    startFuture.complete();
                    logger.debug("All verticles of product micro services are started");
                });
    }
}