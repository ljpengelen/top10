package nl.friendlymirror.top10.account;

import java.util.function.Function;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class GoogleAccountVerticle extends AbstractVerticle {

    public static final String GOOGLE_LOGIN_ADDRESS = "google.login.accountId";

    private static final String GET_ACCOUNT_ID_TEMPLATE = "SELECT a.account_id FROM account a NATURAL JOIN google_account g WHERE g.google_account_id = ?";
    private static final String CREATE_ACCOUNT_TEMPLATE = "INSERT INTO account (name, email_address) VALUES (?, ?)";
    private static final String CREATE_GOOGLE_ACCOUNT_TEMPLATE = "INSERT INTO google_account (account_id, google_account_id) VALUES (?, ?)";

    private final JsonObject jdbcOptions;

    private SQLClient jdbcClient;

    @Override
    public void start() {
        log.info("Starting");

        jdbcClient = JDBCClient.createShared(vertx, jdbcOptions);

        vertx.eventBus().consumer(GOOGLE_LOGIN_ADDRESS, this::handle);
    }

    private void handle(Message<JsonObject> googleUserDataMessage) {
        var googleUserData = googleUserDataMessage.body();
        var googleId = googleUserData.getString("id");
        getAccountId(googleId).onComplete(asyncAccountId -> {
            if (asyncAccountId.failed()) {
                log.error("Unable to retrieve account ID for Google ID \"{}\"", googleId, asyncAccountId.cause());
                googleUserDataMessage.fail(500, "Unable to retrieve account ID");
                return;
            }

            var accountId = asyncAccountId.result();
            if (accountId != null) {
                log.debug("Retrieved account ID for Google ID");
                googleUserDataMessage.reply(accountId);
                return;
            }

            log.debug("Creating account linked with Google ID");
            withTransaction(connection ->
                    createAccount(connection, googleUserData.getString("name"), googleUserData.getString("emailAddress")).compose(newAccountId ->
                            linkAccountWithGoogleId(connection, newAccountId, googleUserData.getString("id")))
            ).onSuccess(newAccountId -> {
                log.debug("Created account linked with Google ID");
                googleUserDataMessage.reply(newAccountId);
            }).onFailure(cause -> {
                var errorMessage = "Unable to create account linked with Google ID";
                log.error(errorMessage, cause);
                googleUserDataMessage.fail(500, errorMessage);
            });
        });
    }

    private <T> Future<T> withTransaction(Function<SQLConnection, Future<T>> query) {
        var promise = Promise.<T> promise();

        jdbcClient.getConnection(asyncConnection -> {
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
        });

        return promise.future();
    }

    private Future<Integer> getAccountId(String googleId) {
        var promise = Promise.<Integer> promise();

        var params = new JsonArray().add(googleId);
        jdbcClient.querySingleWithParams(GET_ACCOUNT_ID_TEMPLATE, params, asyncResult -> {
            if (asyncResult.failed()) {
                var cause = asyncResult.cause();
                log.error("Unable to execute query \"{}\"", GET_ACCOUNT_ID_TEMPLATE, cause);
                promise.fail(asyncResult.cause());
                return;
            }

            var result = asyncResult.result();
            if (result == null) {
                log.debug("Query \"{}\" produced no result", GET_ACCOUNT_ID_TEMPLATE);
                promise.complete(null);
                return;
            }

            var accountId = asyncResult.result().getInteger(0);
            log.debug("Query \"{}\" produced result \"{}\"", GET_ACCOUNT_ID_TEMPLATE, accountId);
            promise.complete(accountId);
        });

        return promise.future();
    }

    private Future<Integer> createAccount(SQLConnection connection, String name, String emailAddress) {
        var promise = Promise.<Integer> promise();

        var params = new JsonArray().add(name).add(emailAddress);
        connection.updateWithParams(CREATE_ACCOUNT_TEMPLATE, params, asyncResult -> {
            if (asyncResult.failed()) {
                var cause = asyncResult.cause();
                log.error("Unable to execute query \"{}\"", CREATE_ACCOUNT_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            var accountId = asyncResult.result().getKeys().getInteger(0);
            log.debug("Query \"{}\" produced result \"{}\"", CREATE_ACCOUNT_TEMPLATE, accountId);
            promise.complete(accountId);
        });

        return promise.future();
    }

    private Future<Integer> linkAccountWithGoogleId(SQLConnection connection, Integer accountId, String googleId) {
        var promise = Promise.<Integer> promise();

        var params = new JsonArray().add(accountId).add(googleId);
        connection.updateWithParams(CREATE_GOOGLE_ACCOUNT_TEMPLATE, params, asyncResult -> {
            if (asyncResult.failed()) {
                var cause = asyncResult.cause();
                log.error("Unable to execute query \"{}\"", CREATE_GOOGLE_ACCOUNT_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            log.debug("Query \"{}\" executed successfully", CREATE_GOOGLE_ACCOUNT_TEMPLATE);
            promise.complete(accountId);
        });

        return promise.future();
    }
}
