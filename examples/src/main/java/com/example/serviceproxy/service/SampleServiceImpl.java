package com.example.serviceproxy.service;

import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SampleServiceImpl implements SampleService {
    private static final Logger logger = LoggerFactory.getLogger(SampleServiceImpl.class);

    @Override
    public SampleService getMessage(Handler<AsyncResult<String>> resultHandler) {
        logger.debug("Invoked getMessage");
        String message = "A message";
        Single.just("A message").subscribe(str -> resultHandler.handle(Future.succeededFuture(message)), error -> resultHandler.handle(Future.failedFuture(error.getMessage())));
        return this;
    }
}
