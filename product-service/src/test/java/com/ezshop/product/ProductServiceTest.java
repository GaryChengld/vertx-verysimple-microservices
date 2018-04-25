package com.ezshop.product;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.serviceproxy.ServiceBinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

/**
 * ProductServiceTest
 *
 * @author Gary Cheng
 */
@RunWith(VertxUnitRunner.class)
public class ProductServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(ProductServiceTest.class);
    private static final String CONFIG_FILE = "src/conf/config.json";
    private Vertx vertx;
    private ProductService productService;
    private JsonObject config;

    @Before
    public void prepare(TestContext context) {
        Async async = context.async();
        this.vertx = Vertx.vertx();
        vertx.fileSystem().rxReadFile(CONFIG_FILE)
                .map(buffer -> buffer.toJsonObject())
                .map(json -> {
                    this.config = json;
                    JDBCClient jdbc = JDBCClient.createShared(vertx, json.getJsonObject("database"));
                    this.productService = ProductService.create(jdbc);
                    new ServiceBinder(vertx.getDelegate())
                            .setAddress(json.getString("serviceProxyAddress"))
                            .register(ProductService.class, ProductService.create(jdbc));
                    return this.productService;
                })
                .subscribe(productService -> async.complete());
        async.awaitSuccess(5000);
    }

    @Test
    public void getAllCategories(TestContext context) {
        assertNotNull(this.productService);
        Async async = context.async();
        this.productService.getAllCategories(ar -> {
            logger.debug(ar.result().encodePrettily());
            async.complete();
        });
        async.awaitSuccess(10000);
    }

    @Test
    public void getAllCategoriesByServiceBus(TestContext context) {
        Async async = context.async();
        com.ezshop.product.reactivex.ProductService service = ProductService.createProxy(vertx, this.config.getString("serviceProxyAddress"));
        service.rxGetAllCategories().subscribe(categories -> {
            logger.debug(categories.encodePrettily());
            async.complete();
        });
        async.awaitSuccess(10000);
    }

    @After
    public void finish(TestContext context) {
        this.vertx.close(context.asyncAssertSuccess());
    }
}