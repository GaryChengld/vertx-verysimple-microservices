package com.example.eventbus;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReceiverVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ReceiverVerticle.class);
    private String name;

    public static void main(String[] args) {
        JsonObject config = new JsonObject();
        config.put("name", "Receiver");
        Vertx.rxClusteredVertx(new VertxOptions().setClustered(true))
                .flatMap(vertx -> vertx.rxDeployVerticle(ReceiverVerticle.class.getName(), new DeploymentOptions().setConfig(config)))
                .subscribe(id -> logger.debug("Deployment successful"));
    }

    @Override
    public void start() {
        this.name = config().getString("name");
        logger.debug("Starting Receiver verticle, name:{}", name);
        vertx.eventBus().<String>consumer("anAddress").toFlowable().subscribe(this::onMessage);
    }

    private void onMessage(Message<String> message) {
        logger.debug("{} Received message: {}", this.name, message.body());
        String replyMessage = "Message replied from receiver[" + this.name + "]";
        message.reply(replyMessage);
    }
}

