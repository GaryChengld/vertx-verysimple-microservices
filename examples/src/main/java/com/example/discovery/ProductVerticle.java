package com.example.discovery;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductVerticle extends BaseServiceVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ProductVerticle.class);

    public static void main(String[] args) {
        JsonObject config = new JsonObject()
                .put(KEY_SERVICE_NAME, "product")
                .put(KEY_PORT, 8081)
                .put(KEY_ROOT, "/service/product");
        Vertx.rxClusteredVertx(new VertxOptions().setClustered(true))
                .flatMap(vertx -> vertx.rxDeployVerticle(ProductVerticle.class.getName(), new DeploymentOptions().setConfig(config)))
                .subscribe(id -> logger.debug("ProductVerticle deployed successfully"));
    }

    @Override
    public void start(Future<Void> startFuture) {
        super.start();
        this.publishHttpServer()
                .subscribe(record -> startFuture.complete(), startFuture::fail);
    }
}