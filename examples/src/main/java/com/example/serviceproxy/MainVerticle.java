package com.example.serviceproxy;


import com.example.serviceproxy.service.SampleService;
import com.example.serviceproxy.service.SampleServiceVerticle;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    com.example.serviceproxy.service.reactivex.SampleService sampleService;

    public static void main(String[] args) {
        Vertx.vertx()
                .rxDeployVerticle(MainVerticle.class.getName())
                .subscribe(deploymentID -> logger.debug("Deployed MainVerticle"));
    }

    @Override
    public void start() {
        logger.debug("Start main verticle");
        vertx.rxDeployVerticle(SampleServiceVerticle.class.getName())
                .map(deploymentId -> {
                    sampleService = SampleService.createProxy(vertx.getDelegate(), SampleService.PROXY_ADDRESS);
                    return deploymentId;
                })
                .flatMap(deploymentId -> sampleService.rxGetMessage())
                .subscribe(message -> logger.debug("Get Message:{}", message));
    }
}

