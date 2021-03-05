package nl.cofx.top10.entity;

import java.util.function.Function;

import io.vertx.core.*;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class AbstractEntityVerticle extends AbstractVerticle {

    protected SQLClient sqlClient;

    protected <T> Future<T> withConnection(Function<SQLConnection, Future<T>> query) {
        return Future.future(promise ->
                sqlClient.getConnection(asyncConnection -> {
                    if (asyncConnection.failed()) {
                        var cause = asyncConnection.cause();
                        log.error("Unable to get connection", cause);
                        promise.fail(cause);
                        return;
                    }

                    var connection = asyncConnection.result();
                    query.apply(connection).onComplete(queryResult -> {
                        connection.close();
                        promise.handle(queryResult);
                    });
                }));
    }

    protected <T> Future<T> withTransaction(Function<SQLConnection, Future<T>> query) {
        return Future.future(promise ->
                sqlClient.getConnection(asyncConnection -> {
                    if (asyncConnection.failed()) {
                        var cause = asyncConnection.cause();
                        log.error("Unable to get connection", cause);
                        promise.fail(cause);
                        return;
                    }

                    var connection = asyncConnection.result();
                    connection.setAutoCommit(false, asyncAutoCommit -> {
                        if (asyncAutoCommit.failed()) {
                            var cause = asyncAutoCommit.cause();
                            log.error("Unable to disable auto commit", cause);
                            connection.close();
                            promise.fail(cause);
                            return;
                        }

                        log.debug("Successfully disabled auto commit");
                        query.apply(connection).onSuccess(t -> connection.commit(asyncCommit -> {
                            if (asyncCommit.failed()) {
                                var cause = asyncCommit.cause();
                                log.error("Unable to commit transaction", cause);
                                connection.close();
                                promise.fail(cause);
                                return;
                            }

                            log.debug("Successfully committed transaction");
                            connection.close();
                            promise.complete(t);
                        })).onFailure(queryCause -> connection.rollback(asyncRollback -> {
                            if (asyncRollback.failed()) {
                                var cause = asyncRollback.cause();
                                log.error("Unable to rollback transaction", cause);
                                connection.close();
                                promise.fail(cause);
                                return;
                            }

                            log.debug("Successfully rolled back transaction");
                            connection.close();
                            promise.fail(queryCause);
                        }));
                    });
                }));
    }
}
