package com.example.eventbus;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventBusApp {
    private static final Logger logger = LoggerFactory.getLogger(ReceiverVerticle.class);

    public static void main(String[] args) {
        logger.debug("Start deploy receivers");
        Vertx vertx = Vertx.vertx();
        Single<String> deployment1 = deployReceiver(vertx, "Receiver-1");
        Single<String> deployment2 = deployReceiver(vertx, "Receiver-2");
        Single.zipArray(ids -> ids, deployment1, deployment2)
                .flatMap(ids -> vertx.rxDeployVerticle(SenderVerticle.class.getName()))
                .subscribe(id -> logger.debug("Deployment successful"));
    }

    private static Single<String> deployReceiver(Vertx vertx, String name) {
        JsonObject config = new JsonObject();
        config.put("name", name);
        return vertx.rxDeployVerticle(ReceiverVerticle.class.getName(), new DeploymentOptions().setConfig(config));
    }

}
