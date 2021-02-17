package nl.cofx.top10.account;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.entity.AbstractEntityVerticle;
import nl.cofx.top10.random.TokenGenerator;

@Log4j2
@RequiredArgsConstructor
public class GoogleAccountVerticle extends AbstractEntityVerticle {

    public static final String GOOGLE_LOGIN_ADDRESS = "google.login.accountId";

    private static final String GET_ACCOUNT_TEMPLATE = "SELECT a.account_id, a.name, a.email_address FROM account a NATURAL JOIN google_account g WHERE g.google_account_id = ?";
    private static final String UPDATE_STATISTICS_TEMPLATE = "UPDATE account SET last_login_at = NOW(), number_of_logins = number_of_logins + 1 WHERE account_id = ?";
    private static final String CREATE_ACCOUNT_TEMPLATE = "INSERT INTO account (name, email_address, first_login_at, last_login_at, number_of_logins, external_id) VALUES (?, ?, NOW(), NOW(), 1, ?)";
    private static final String CREATE_GOOGLE_ACCOUNT_TEMPLATE = "INSERT INTO google_account (account_id, google_account_id) VALUES (?, ?)";

    private final JsonObject jdbcOptions;

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        vertx.eventBus().consumer(GOOGLE_LOGIN_ADDRESS, this::handle);
    }

    private void handle(Message<JsonObject> googleUserDataMessage) {
        var googleUserData = googleUserDataMessage.body();
        var googleId = googleUserData.getString("id");
        getAccount(googleId).onComplete(asyncAccount -> {
            if (asyncAccount.failed()) {
                log.error("Unable to retrieve account for Google ID \"{}\"", googleId, asyncAccount.cause());
                googleUserDataMessage.fail(500, "Unable to retrieve account");
                return;
            }

            var account = asyncAccount.result();
            if (account != null) {
                log.debug("Retrieved account for Google ID");
                updateStatisticsForAccount(account.getInteger("accountId")).onComplete(asyncUpdate -> {
                    if (asyncUpdate.failed()) {
                        log.error("Unable to update login statistics for account \"{}\"", account, asyncUpdate.cause());
                    } else {
                        log.debug("Updated login statistics for account");
                    }
                    googleUserDataMessage.reply(account);
                });
                return;
            }

            log.debug("Creating account linked with Google ID");
            var name = googleUserData.getString("name");
            var emailAddress = googleUserData.getString("emailAddress");
            withTransaction(connection ->
                    createAccount(connection, name, emailAddress).compose(newAccountId ->
                            linkAccountWithGoogleId(connection, newAccountId, googleUserData.getString("id")))
            ).onSuccess(newAccountId -> {
                log.debug("Created account linked with Google ID");
                var newAccount = new JsonObject()
                        .put("accountId", newAccountId)
                        .put("name", name)
                        .put("emailAddress", emailAddress);
                googleUserDataMessage.reply(newAccount);
            }).onFailure(cause -> {
                var errorMessage = "Unable to create account linked with Google ID";
                log.error(errorMessage, cause);
                googleUserDataMessage.fail(500, errorMessage);
            });
        });
    }

    private Future<JsonObject> getAccount(String googleId) {
        var promise = Promise.<JsonObject> promise();

        var params = new JsonArray().add(googleId);
        sqlClient.querySingleWithParams(GET_ACCOUNT_TEMPLATE, params, asyncResult -> {
            if (asyncResult.failed()) {
                var cause = asyncResult.cause();
                log.error("Unable to execute query \"{}\"", GET_ACCOUNT_TEMPLATE, cause);
                promise.fail(asyncResult.cause());
                return;
            }

            var result = asyncResult.result();
            if (result == null) {
                log.debug("Query \"{}\" produced no result", GET_ACCOUNT_TEMPLATE);
                promise.complete(null);
                return;
            }

            var account = new JsonObject()
                    .put("accountId", result.getInteger(0))
                    .put("name", result.getString(1))
                    .put("emailAddress", result.getString(2));
            log.debug("Query \"{}\" produced result \"{}\"", GET_ACCOUNT_TEMPLATE, account);
            promise.complete(account);
        });

        return promise.future();
    }

    private Future<Void> updateStatisticsForAccount(int accountId) {
        var promise = Promise.<Void> promise();

        var params = new JsonArray().add(accountId);
        sqlClient.querySingleWithParams(UPDATE_STATISTICS_TEMPLATE, params, asyncResult -> {
            if (asyncResult.failed()) {
                var cause = asyncResult.cause();
                log.error("Unable to execute query \"{}\"", UPDATE_STATISTICS_TEMPLATE, cause);
                promise.fail(asyncResult.cause());
                return;
            }

            promise.complete();
        });

        return promise.future();
    }

    private Future<Integer> createAccount(SQLConnection connection, String name, String emailAddress) {
        var promise = Promise.<Integer> promise();

        var externalId = TokenGenerator.generateToken();
        var params = new JsonArray().add(name).add(emailAddress).add(externalId);
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
