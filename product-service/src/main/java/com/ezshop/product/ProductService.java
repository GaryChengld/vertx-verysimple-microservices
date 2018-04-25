package com.ezshop.product;

import com.ezshop.product.impl.ProductServiceImpl;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.jdbc.JDBCClient;

/**
 * The interface of service which maintain products and categories
 *
 * @author Gary Cheng
 */
@ProxyGen
@VertxGen
public interface ProductService {
    @GenIgnore
    static ProductService create(JDBCClient jdbc) {
        return new ProductServiceImpl(jdbc);
    }

    /**
     * Create service proxy by given proxy address on event bus
     *
     * @param vertx
     * @param address the address of proxy on event bus
     * @return
     */
    @GenIgnore
    static com.ezshop.product.reactivex.ProductService createProxy(Vertx vertx, String address) {
        return new com.ezshop.product.reactivex.ProductService(new ProductServiceVertxEBProxy(vertx.getDelegate(), address));
    }

    /**
     * Return all categories
     *
     * @param resultHandler the handler to process async result
     * @return
     */
    @Fluent
    ProductService getAllCategories(Handler<AsyncResult<JsonArray>> resultHandler);
}
