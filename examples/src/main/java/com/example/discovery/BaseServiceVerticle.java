package com.example.discovery;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class BaseServiceVerticle extends AbstractVerticle {
    protected static final String KEY_SERVICE_NAME = "service.name";
    protected static final String KEY_HOST = "host";
    protected static final String KEY_PORT = "port";
    protected static final String KEY_ROOT = "root";
    private static final Logger logger = LoggerFactory.getLogger(BaseServiceVerticle.class);
    protected ServiceDiscovery discovery;
    private Set<Record> publishedRecords = new ConcurrentHashSet<>();

    @Override
    public void start() {
        logger.debug("Starting {} verticle", this.config().getString(KEY_SERVICE_NAME));
        logger.debug("Config:{}", this.config().encodePrettily());
        this.discovery = ServiceDiscovery.create(vertx);
    }

    @Override
    public void stop() {
        logger.debug("Stopping {} verticle", this.config().getString(KEY_SERVICE_NAME));
        if (null != discovery) {
            List<Completable> completables = new ArrayList<>();
            publishedRecords.forEach(record -> completables.add(discovery.rxUnpublish(record.getRegistration())));
            Completable.merge(completables)
                    .subscribe(() -> {
                        logger.debug("All services have been un-published");
                        discovery.close();
                    });
        }
    }

    protected Handler<HttpServerRequest> getHttpRequestHandler() {
        Router router = Router.router(vertx);
        router.route().handler(routingContext -> {
            logger.debug("Received a http request");
            routingContext.response()
                    .putHeader("content-type", "text/json")
                    .end(new JsonObject().put("message", "Welcome to " + this.config().getString(KEY_SERVICE_NAME) + " service").encodePrettily());
        });
        return router::accept;
    }

    protected final Single<Record> publishHttpServer() {
        String serviceName = this.config().getString(KEY_SERVICE_NAME);
        String host = this.config().getString(KEY_HOST, "localhost");
        Integer port = this.config().getInteger(KEY_PORT, 8080);
        String root = this.config().getString(KEY_ROOT, "/");
        return vertx.createHttpServer()
                .requestHandler(this.getHttpRequestHandler())
                .rxListen(port)
                .flatMap(httpServer -> {
                    logger.debug("{} http server started on port {}", serviceName, port);
                    return this.publishRecord(HttpEndpoint.createRecord(serviceName, host, port, root));
                });
    }

    protected final Single<Record> publishRecord(Record record) {
        return discovery.rxPublish(record)
                .flatMap(r -> {
                    logger.debug("Service {} has been published", record.getName());
                    this.publishedRecords.add(r);
                    return Single.just(r);
                });
    }
}