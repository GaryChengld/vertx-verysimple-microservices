package com.ezshop.liquibase;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * LiquibaseVerticle
 *
 * @author Gary Cheng
 */
public class LiquibaseVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(LiquibaseVerticle.class);
    private static final String CONFIG_FILE = "src/conf/config.json";

    public static void main(String[] args) {
        logger.debug("Start running...");
        Vertx vertx = Vertx.vertx();
        vertx.fileSystem().rxReadFile(CONFIG_FILE)
                .flatMap(buffer -> Single.just(buffer.toJsonObject()))
                .flatMap(config -> vertx.rxDeployVerticle(LiquibaseVerticle.class.getName(), new DeploymentOptions().setConfig(config)))
                .subscribe(id -> logger.debug("Vertical deployed, deployment ID:{}", id));
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        logger.debug("config:{}", this.config().encodePrettily());
        JsonObject dbConfig = this.config().getJsonObject("database");
        JsonObject liquibaseConfig = this.config().getJsonObject("liquibase");
        Class.forName(dbConfig.getString("driver_class"));
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(dbConfig.getString("url", dbConfig.getString("user", dbConfig.getString("password"))));
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));

            Liquibase liquibase = new Liquibase(liquibaseConfig.getString("change_log"), new ClassLoaderResourceAccessor(), database);
            liquibase.update("update");
            startFuture.complete();
        } finally {
            if (null != conn) {
                conn.close();
            }
        }
    }
}
