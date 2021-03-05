package nl.cofx.top10.account;

import static nl.cofx.top10.postgresql.PostgreSql.toUuid;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.entity.AbstractEntityVerticle;

@Log4j2
@RequiredArgsConstructor
public class ExternalAccountVerticle extends AbstractEntityVerticle {

    public static final String EXTERNAL_LOGIN_ADDRESS = "external.login.accountId";

    private static final String GET_ACCOUNT_BY_GOOGLE_ID_TEMPLATE = "SELECT replace(a.account_id::text, '-', '') AS account_id, a.name, a.email_address FROM account a NATURAL JOIN google_account g WHERE g.google_account_id = ?";
    private static final String GET_ACCOUNT_BY_MICROSOFT_ID_TEMPLATE = "SELECT replace(a.account_id::text, '-', '') AS account_id, a.name, a.email_address FROM account a NATURAL JOIN microsoft_account g WHERE g.microsoft_account_id = ?";
    private static final String UPDATE_STATISTICS_TEMPLATE = "UPDATE account SET last_login_at = NOW(), number_of_logins = number_of_logins + 1 WHERE account_id = ?";
    private static final String CREATE_ACCOUNT_TEMPLATE = "INSERT INTO account (name, email_address, first_login_at, last_login_at, number_of_logins) VALUES (?, ?, NOW(), NOW(), 1)";
    private static final String CREATE_GOOGLE_ACCOUNT_TEMPLATE = "INSERT INTO google_account (account_id, google_account_id) VALUES (?, ?)";
    private static final String CREATE_MICROSOFT_ACCOUNT_TEMPLATE = "INSERT INTO microsoft_account (account_id, microsoft_account_id) VALUES (?, ?)";

    private final JsonObject jdbcOptions;

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        vertx.eventBus().consumer(EXTERNAL_LOGIN_ADDRESS, this::handle);
    }

    private void handle(Message<JsonObject> externalUserMessage) {
        var externalUser = externalUserMessage.body();
        var id = externalUser.getString("id");
        var provider = externalUser.getString("provider");
        getAccount(id, provider).onComplete(asyncAccount -> {
            if (asyncAccount.failed()) {
                log.error("Unable to retrieve account for external ID \"{}\" and provider \"{}\"", id, provider, asyncAccount.cause());
                externalUserMessage.fail(500, "Unable to retrieve account");
                return;
            }

            var account = asyncAccount.result();
            if (account != null) {
                log.debug("Retrieved account for external ID");
                updateStatisticsForAccount(account.getString("accountId")).onComplete(asyncUpdate -> {
                    if (asyncUpdate.failed()) {
                        log.error("Unable to update login statistics for account \"{}\"", account, asyncUpdate.cause());
                    } else {
                        log.debug("Updated login statistics for account");
                    }
                    externalUserMessage.reply(account);
                });
                return;
            }

            log.debug("Creating account linked with external ID");
            var name = externalUser.getString("name");
            var emailAddress = externalUser.getString("emailAddress");
            withTransaction(connection ->
                    createAccount(connection, name, emailAddress).compose(newAccountId ->
                            linkAccountWithExternalId(connection, newAccountId, id, provider))
            ).onSuccess(newAccountId -> {
                log.debug("Created account linked with external ID");
                var newAccount = new JsonObject()
                        .put("accountId", newAccountId)
                        .put("name", name)
                        .put("emailAddress", emailAddress);
                externalUserMessage.reply(newAccount);
            }).onFailure(cause -> {
                var errorMessage = "Unable to create account linked with external ID";
                log.error(errorMessage, cause);
                externalUserMessage.fail(500, errorMessage);
            });
        });
    }

    private Future<JsonObject> getAccount(String id, String provider) {
        return Future.future(promise -> {
            var params = new JsonArray().add(id);
            var template = getRetrievalTemplate(provider);
            sqlClient.querySingleWithParams(template, params, asyncResult -> {
                if (asyncResult.failed()) {
                    var cause = asyncResult.cause();
                    log.error("Unable to execute query \"{}\"", template, cause);
                    promise.fail(asyncResult.cause());
                    return;
                }

                var result = asyncResult.result();
                if (result == null) {
                    log.debug("Query \"{}\" produced no result", template);
                    promise.complete(null);
                    return;
                }

                var account = new JsonObject()
                        .put("accountId", result.getString(0))
                        .put("name", result.getString(1))
                        .put("emailAddress", result.getString(2));
                log.debug("Query \"{}\" produced result \"{}\"", template, account);
                promise.complete(account);
            });
        });
    }

    private String getRetrievalTemplate(String provider) {
        switch (provider) {
            case "google":
                return GET_ACCOUNT_BY_GOOGLE_ID_TEMPLATE;
            case "microsoft":
                return GET_ACCOUNT_BY_MICROSOFT_ID_TEMPLATE;
            default:
                throw new IllegalStateException(String.format("Unexpected provider: \"%s\"", provider));
        }
    }

    private Future<Void> updateStatisticsForAccount(String accountId) {
        return Future.future(promise -> {
            var params = new JsonArray().add(toUuid(accountId));
            sqlClient.querySingleWithParams(UPDATE_STATISTICS_TEMPLATE, params, asyncResult -> {
                if (asyncResult.failed()) {
                    var cause = asyncResult.cause();
                    log.error("Unable to execute query \"{}\"", UPDATE_STATISTICS_TEMPLATE, cause);
                    promise.fail(asyncResult.cause());
                    return;
                }

                promise.complete();
            });
        });
    }

    private Future<String> createAccount(SQLConnection connection, String name, String emailAddress) {
        return Future.future(promise -> {
            var params = new JsonArray().add(name).add(emailAddress);
            connection.updateWithParams(CREATE_ACCOUNT_TEMPLATE, params, asyncResult -> {
                if (asyncResult.failed()) {
                    var cause = asyncResult.cause();
                    log.error("Unable to execute query \"{}\"", CREATE_ACCOUNT_TEMPLATE, cause);
                    promise.fail(cause);
                    return;
                }

                var accountId = asyncResult.result().getKeys().getString(5).replace("-", "");
                log.debug("Query \"{}\" produced result \"{}\"", CREATE_ACCOUNT_TEMPLATE, accountId);
                promise.complete(accountId);
            });
        });
    }

    private Future<String> linkAccountWithExternalId(SQLConnection connection, String accountId, String externalId, String provider) {
        return Future.future(promise -> {
            var template = getCreationTemplate(provider);
            var params = new JsonArray().add(toUuid(accountId)).add(externalId);
            connection.updateWithParams(template, params, asyncResult -> {
                if (asyncResult.failed()) {
                    var cause = asyncResult.cause();
                    log.error("Unable to execute query \"{}\"", template, cause);
                    promise.fail(cause);
                    return;
                }

                log.debug("Query \"{}\" executed successfully", template);
                promise.complete(accountId);
            });
        });
    }

    private String getCreationTemplate(String provider) {
        switch (provider) {
            case "google":
                return CREATE_GOOGLE_ACCOUNT_TEMPLATE;
            case "microsoft":
                return CREATE_MICROSOFT_ACCOUNT_TEMPLATE;
            default:
                throw new IllegalStateException(String.format("Invalid provider: \"%s\"", provider));
        }
    }
}
