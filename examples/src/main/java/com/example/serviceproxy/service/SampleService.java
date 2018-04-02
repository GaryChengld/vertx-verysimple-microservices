package com.example.serviceproxy.service;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

@ProxyGen
@VertxGen
public interface SampleService {
    String PROXY_ADDRESS = "productService.proxy.address";

    @GenIgnore
    static SampleService create() {
        return new SampleServiceImpl();
    }

    @GenIgnore
    static com.example.serviceproxy.service.reactivex.SampleService createProxy(Vertx vertx, String address) {
        return new com.example.serviceproxy.service.reactivex.SampleService(new SampleServiceVertxEBProxy(vertx, address));
    }

    @Fluent
    SampleService getMessage(Handler<AsyncResult<String>> resultHandler);

}
