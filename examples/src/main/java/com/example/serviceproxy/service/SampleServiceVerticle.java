package com.example.serviceproxy.service;

import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleServiceVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(SampleServiceVerticle.class);

    @Override
    public void start() {
        logger.debug("Starting ProductServiceVerticle");
        new ServiceBinder(vertx.getDelegate())
                .setAddress(SampleService.PROXY_ADDRESS)
                .register(SampleService.class, SampleService.create());
    }
}
