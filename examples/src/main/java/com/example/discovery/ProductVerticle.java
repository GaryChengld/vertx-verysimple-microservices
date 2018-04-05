package com.example.discovery;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.discovery.ProductHttpVerticle.PORT;

public class ProductVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ProductVerticle.class);

    private static final String SERVICE_NAME = "product";

    private ServiceDiscovery discovery;
    private Record record;

    public static void main(String[] args) {
        Vertx.rxClusteredVertx(new VertxOptions().setClustered(true))
                .flatMap(vertx -> vertx.rxDeployVerticle(ProductVerticle.class.getName()))
                .subscribe(id -> logger.debug("ProductVerticle deployed successfully"));
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        logger.debug("Starting ProductVerticle");
        this.discovery = ServiceDiscovery.create(vertx);
        DeploymentOptions options = new DeploymentOptions().setConfig(config());
        vertx.rxDeployVerticle(ProductHttpVerticle.class.getName(), options)
                .flatMap(id -> discovery.rxPublish(HttpEndpoint.createRecord(SERVICE_NAME, "localhost", PORT, "/service/product")))
                .subscribe(record -> {
                    this.record = record;
                    logger.debug("{} service has been published", SERVICE_NAME);
                    startFuture.complete();
                }, startFuture::fail);
    }

    @Override
    public void stop(Future<Void> stopFuture) {
        if (null != discovery && null != record) {
            discovery.rxUnpublish(this.record.getRegistration())
                    .subscribe(() -> {
                        logger.debug("Service {} has been un-published", SERVICE_NAME);
                        stopFuture.complete();
                    });
        } else {
            stopFuture.complete();
        }
    }
}