package com.ezshop.product.impl;

import com.ezshop.common.database.JDBCClientRepository;
import com.ezshop.product.ProductService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The implementation of ProductService
 *
 * @author Gary Cheng
 */
public class ProductServiceImpl implements ProductService {
    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final String SQL_GET_ALL_CATEGORIES = "SELECT * FROM CATEGORY ORDER BY CATEGORY_NAME";

    private JDBCClient jdbc;
    private JDBCClientRepository repository;

    public ProductServiceImpl(JDBCClient jdbc) {
        this.jdbc = jdbc;
        this.repository = JDBCClientRepository.create(jdbc);
    }

    @Override
    public ProductService getAllCategories(Handler<AsyncResult<JsonArray>> resultHandler) {
        logger.debug("getAllCategories is invoked");
        repository.query(SQL_GET_ALL_CATEGORIES, this::convertCategory)
                .subscribe(categories -> resultHandler.handle(Future.succeededFuture(new JsonArray(categories))),
                        t -> resultHandler.handle(Future.failedFuture(t)));
        return this;
    }

    private JsonObject convertCategory(JsonObject row) {
        JsonObject category = new JsonObject();
        category.put("categoryId", row.getValue("CATEGORY_ID"));
        category.put("categoryName", row.getValue("CATEGORY_NAME"));
        return category;
    }
}