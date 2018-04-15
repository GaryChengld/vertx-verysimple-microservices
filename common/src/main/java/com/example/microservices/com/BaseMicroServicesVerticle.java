package com.example.microservices.com;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The base verticle class which provided some common functions include service discovery
 *
 * @author Gary Cheng
 */
public abstract class BaseMicroServicesVerticle extends AbstractVerticle {
    public static final String KEY_SERVICE_NAME = "service.name";
    public static final String KEY_HOST = "host";
    public static final String KEY_PORT = "port";
    public static final String KEY_ROOT = "root";
    private static final String KEY_NAME = "name";

    private static final Logger logger = LoggerFactory.getLogger(BaseMicroServicesVerticle.class);

    protected ServiceDiscovery discovery;
    private Collection<Record> publishedRecords = new ConcurrentHashSet<>();
    private Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private Map<String, Object> serviceClientMap = new ConcurrentHashMap<>();

    @Override
    public void start() {
        logger.debug("Starting verticle - {}", this.getClass().getName());
        logger.debug("Config:{}", this.config().encodePrettily());
        this.discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions().setBackendConfiguration(this.getServiceDiscoveryConfig()));
    }

    @Override
    public void stop() {
        logger.debug("Stopping verticle - {}", this.getClass().getName());
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

    /**
     * Return Service discovery configure, the default implementation store them in verticle's config()
     *
     * @return Service discovery configure
     */
    protected JsonObject getServiceDiscoveryConfig() {
        return this.config();
    }

    /**
     * Return circuit breaker of service by service name
     *
     * @param serviceName the name of service
     * @return circuit breaker of service
     */
    protected final CircuitBreaker getCircuitBreaker(String serviceName) {
        CircuitBreaker circuitBreaker = circuitBreakerMap.get(serviceName);
        if (null == circuitBreaker) {
            circuitBreaker = this.createCircuitBreaker(serviceName);
            this.circuitBreakerMap.put(serviceName, circuitBreaker);
        }
        return circuitBreaker;
    }

    /**
     * Create circuit breaker for service
     *
     * @param serviceName the name of service
     * @return circuit breaker of service
     */
    protected CircuitBreaker createCircuitBreaker(String serviceName) {
        logger.debug("create circuit breaker for service {}", serviceName);
        String circuitBreakerName = serviceName + "-" + "circuit-breaker";
        CircuitBreakerOptions options = new CircuitBreakerOptions()
                .setMaxFailures(5)
                .setTimeout(5000)
                .setResetTimeout(10000)
                .setFallbackOnFailure(true);
        return CircuitBreaker.create(circuitBreakerName, vertx, options)
                .openHandler(v -> logger.debug("{} opened", circuitBreakerName))
                .halfOpenHandler(v -> logger.debug("{} half opened", circuitBreakerName))
                .closeHandler(v -> logger.debug("{} closed", circuitBreakerName));
    }

    /**
     * Lookup ServiceDiscovery by service name and return an async HttpClient
     *
     * @param serviceName the name of service
     * @return
     */
    protected final Single<HttpClient> getServiceHttpClient(String serviceName) {
        logger.debug("Get HTTP client by service name[{}]", serviceName);
        Object serviceClient = this.serviceClientMap.get(serviceName);
        if (null != serviceClient) {
            return Single.just((HttpClient) serviceClient);
        } else {
            return HttpEndpoint.rxGetClient(discovery, new JsonObject().put(KEY_NAME, serviceName))
                    .doOnSuccess(httpClient -> this.serviceClientMap.put(serviceName, httpClient));
        }
    }

    /**
     * Publish a HttpEndPoint service to ServiceDiscovery
     *
     * @param config the configure of HttpEndPoint
     * @return
     */
    protected final Single<Record> publishHttpEndPoint(JsonObject config) {
        String serviceName = config.getString(KEY_SERVICE_NAME);
        String host = config.getString(KEY_HOST, "localhost");
        Integer port = config.getInteger(KEY_PORT, 8080);
        String root = config.getString(KEY_ROOT, "/");
        return this.publishRecord(HttpEndpoint.createRecord(serviceName, host, port, root));
    }

    /**
     * Publish a service record to ServiceDiscovery
     *
     * @param record record to publish
     * @return
     */
    private Single<Record> publishRecord(Record record) {
        return discovery.rxPublish(record)
                .flatMap(r -> {
                    logger.debug("Service {} has been published", record.getName());
                    this.publishedRecords.add(r);
                    return Single.just(r);
                });
    }
}