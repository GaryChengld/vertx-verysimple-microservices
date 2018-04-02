package com.example.jdbc;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(JdbcVerticle.class);
    private static final String CONFIG_FILE = "examples/src/config/app-conf.json";
    private static final String SQL_CREATE_TABLE = "CREATE TABLE Product(PROD_ID VARCHAR(20) PRIMARY KEY, PROD_NAME VARCHAR(200) NOT NULL)";
    private static final String SQL_INSERT_PRODUCT = "INSERT INTO Product(PROD_ID, PROD_NAME) VALUES (?, ?)";
    private static final String SQL_SELECT_PRODUCTS = "SELECT * FROM Product";

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.fileSystem().rxReadFile(CONFIG_FILE)
                .flatMap(buffer -> Single.just(buffer.toJsonObject()))
                .flatMap(config -> vertx.rxDeployVerticle(JdbcVerticle.class.getName(), new DeploymentOptions().setConfig(config)))
                .subscribe(id -> logger.debug("Vertical deployed, deployment ID:{}", id));
    }

    @Override
    public void start() throws Exception {
        logger.debug("Verticle RxJDBCExample started");
        JsonObject config = this.config();
        logger.debug("Config:{}", config.toString());
        JDBCClient jdbc = JDBCClient.createShared(vertx, config);

        jdbc.rxGetConnection().flatMapPublisher(conn ->
                conn.rxUpdate(SQL_CREATE_TABLE)
                        .flatMap(res -> this.insertProduct(conn, "1", "Product 1"))
                        .flatMap(res -> this.insertProduct(conn, "2", "Product 2"))
                        .flatMap(res -> conn.rxQuery(SQL_SELECT_PRODUCTS))
                        .flattenAsFlowable(resultSet -> resultSet.getRows())
                        .doAfterTerminate(conn::close)
        ).subscribe(json -> logger.debug(json.encodePrettily()));
    }

    private Single<UpdateResult> insertProduct(SQLConnection conn, String productId, String productName) {
        logger.debug("Add product id:{}, name:{}", productId, productName);
        JsonArray params = new JsonArray().add(productId).add(productName);
        return conn.rxUpdateWithParams(SQL_INSERT_PRODUCT, params);
    }
}
