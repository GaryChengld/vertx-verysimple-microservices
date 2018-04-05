package com.example.eventbus;

import io.vertx.core.VertxOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SenderVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(SenderVerticle.class);

    public static void main(String[] args) {
        Vertx.rxClusteredVertx(new VertxOptions().setClustered(true))
                .flatMap(vertx -> vertx.rxDeployVerticle(SenderVerticle.class.getName()))
                .subscribe(id -> logger.debug("Deployment successful"));
    }

    @Override
    public void start() {
        logger.debug("Start Sender verticle");
        EventBus eventBus = vertx.eventBus();
        eventBus.publish("anAddress", "Published a message");

        vertx.setPeriodic(1000, id ->
                eventBus.rxSend("anAddress", "A message").subscribe(reply -> logger.debug("Received reply:{}", reply.body()))
        );
    }
}
