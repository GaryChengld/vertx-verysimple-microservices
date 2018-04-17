package com.example.microservices.com;

import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The base verticle class which provided some common functions include service discovery
 *
 * @author Gary Cheng
 */
public abstract class BaseMicroServicesVerticle extends AbstractVerticle {
    protected static final String KEY_SERVICE_NAME = "service.name";
    protected static final String KEY_HOST = "host";
    protected static final String KEY_PORT = "port";
    protected static final String KEY_ROOT = "root";
    protected static final String KEY_NAME = "name";

    private static final Logger logger = LoggerFactory.getLogger(BaseMicroServicesVerticle.class);

    protected ServiceDiscovery discovery;
    private Record publishedRecord;
    private Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private Map<String, Object> serviceEndPointMap = new ConcurrentHashMap<>();

    @Override
    public void start() {
        logger.debug("Starting verticle - {}", this.getClass().getName());
        logger.debug("Config:{}", this.config().encodePrettily());
        this.discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions().setBackendConfiguration(this.getServiceDiscoveryConfig()));
    }

    @Override
    public void stop() {
        logger.debug("Stopping verticle - {}", this.getClass().getName());
        this.serviceEndPointMap.keySet().stream().forEach(serviceName -> this.closeCachedEndPoint(serviceName));
        this.serviceEndPointMap.clear();
        this.circuitBreakerMap.values().stream().forEach(circuitBreaker -> circuitBreaker.close());
        this.circuitBreakerMap.clear();
        this.unpublishRecord().subscribe(b -> discovery.close());
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
        logger.debug("Get CircuitBreaker of service {}", serviceName);
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
        logger.debug("Create CircuitBreaker for service {}", serviceName);
        String circuitBreakerName = serviceName + "-" + "circuit-breaker";
        CircuitBreakerOptions options = new CircuitBreakerOptions()
                .setMaxFailures(5)
                .setTimeout(5000)
                .setResetTimeout(10000)
                .setFallbackOnFailure(true);
        return CircuitBreaker.create(circuitBreakerName, vertx, options)
                .openHandler(v -> {
                    logger.debug("{} opened", circuitBreakerName);
                    this.closeCachedEndPoint(serviceName);
                })
                .halfOpenHandler(v -> logger.debug("{} half opened", circuitBreakerName))
                .closeHandler(v -> logger.debug("{} closed", circuitBreakerName));
    }

    /**
     * Return an async HttpClient by service name
     *
     * @param serviceName the name of service
     * @return
     */
    protected final Single<HttpClient> getHttpEndPoint(String serviceName) {
        logger.debug("Get HTTP client by service name[{}]", serviceName);
        Object endPoint = this.serviceEndPointMap.get(serviceName);
        if (null != endPoint && endPoint instanceof HttpClient) {
            return Single.just((HttpClient) endPoint);
        } else {
            return HttpEndpoint.rxGetClient(discovery, new JsonObject().put(KEY_NAME, serviceName))
                    .doOnSuccess(httpClient -> this.serviceEndPointMap.put(serviceName, httpClient));
        }
    }

    /**
     * Return an async HttpClient by service name
     *
     * @param serviceName the name of service
     * @return
     */
    protected final Single<WebClient> getWebEndPoint(String serviceName) {
        logger.debug("Get Web client by service name[{}]", serviceName);
        Object endPoint = this.serviceEndPointMap.get(serviceName);
        if (null != endPoint && endPoint instanceof WebClient) {
            return Single.just((WebClient) endPoint);
        } else {
            logger.debug("Getting Web client from ServiceDiscovery...");
            return HttpEndpoint.rxGetWebClient(discovery, new JsonObject().put(KEY_NAME, serviceName))
                    .doOnSuccess(webClient -> this.serviceEndPointMap.put(serviceName, webClient));
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
    protected Single<Record> publishRecord(Record record) {
        return this.unpublishRecord()
                .flatMap(b -> discovery.rxPublish(record))
                .doOnSuccess(r -> this.publishedRecord = r);
    }

    /**
     * Invoke a restful service by service name
     *
     * @param serviceName the name of service
     * @param method      HTTP method
     * @param uri         uri of request
     * @param body        body of request
     * @return result as JsonObject
     */
    protected Single<JsonObject> invokeRestfulService(String serviceName, HttpMethod method, String uri, JsonObject body) {
        logger.debug("invokeRestfulService, service name:{}, uri:{}", serviceName, uri);
        return this.getCircuitBreaker(serviceName).rxExecuteCommand(future ->
                this.getWebEndPoint(serviceName).subscribe(webClient -> {
                    HttpRequest request = webClient.request(method, uri);
                    Single<HttpResponse<Buffer>> result;
                    if (null == body) {
                        result = request.rxSend();
                    } else {
                        result = request.rxSendJsonObject(body);
                    }
                    result.map(HttpResponse::bodyAsJsonObject).subscribe(future::complete, future::fail);
                }, throwable -> future.fail("Service [" + serviceName + "] not found"))
        );
    }

    /**
     * Invoke a restful service by given host and port
     *
     * @param method HTTP method
     * @param port   port of EndPoint
     * @param host   host of EndPoint
     * @param uri    uri of request
     * @param body   body of request
     * @return
     */
    protected Single<JsonObject> invokeRestful(HttpMethod method, int port, String host, String uri, JsonObject body) {
        logger.debug("invokeRestfulService, host:{}, port:{}, uri:{}", host, port, uri);
        HttpRequest<Buffer> request = WebClient.create(vertx).request(method, port, host, uri);
        Single<HttpResponse<Buffer>> result;
        if (null == body) {
            result = request.rxSend();
        } else {
            result = request.rxSendJsonObject(body);
        }
        return result.map(HttpResponse::bodyAsJsonObject);
    }

    private Single<Boolean> unpublishRecord() {
        return Single.create(emitter -> {
            if (null != this.publishedRecord) {
                discovery.rxUnpublish(this.publishedRecord.getRegistration())
                        .subscribe(() -> {
                            logger.debug("Service {} unpublished", this.publishedRecord.getName());
                            this.publishedRecord = null;
                            emitter.onSuccess(true);
                        }, emitter::onError);
            } else {
                emitter.onSuccess(true);
            }
        });
    }

    private void closeCachedEndPoint(String serviceName) {
        Object endPoint = this.serviceEndPointMap.get(serviceName);
        if (null != endPoint) {
            logger.debug("Close cached EndPoint for service {}", serviceName);
            if (endPoint instanceof WebClient) {
                ((WebClient) endPoint).close();
            } else if (endPoint instanceof HttpClient) {
                ((HttpClient) endPoint).close();
            }
            this.serviceEndPointMap.remove(serviceName);
        }
    }
}