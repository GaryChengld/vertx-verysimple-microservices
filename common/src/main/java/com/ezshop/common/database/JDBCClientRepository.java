package com.ezshop.common.database;

import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.jdbc.JDBCClient;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JDBCClientRepository
 *
 * @author Gary Cheng
 */
public class JDBCClientRepository {
    private JDBCClient jdbcClient;

    /**
     * Create a JDBCClient repository
     *
     * @param jdbcClient JDBCClient instance
     * @return
     */
    public static JDBCClientRepository create(JDBCClient jdbcClient) {
        JDBCClientRepository repository = new JDBCClientRepository();
        repository.jdbcClient = jdbcClient;
        return repository;
    }

    /**
     * Execute a single SQL statement without parameter
     *
     * @param sql
     * @param mapper map a jonObject row to target type
     * @return
     */
    public <R> Single<List<R>> query(String sql, Function<JsonObject, ? extends R> mapper) {
        return jdbcClient.rxGetConnection().flatMap(
                conn -> jdbcClient.rxQuery(sql)
                        .doAfterTerminate(conn::close)
                        .map(resultSet -> resultSet.getRows().stream().map(mapper::apply).collect(Collectors.toList()))
        );
    }
}
