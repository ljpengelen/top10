package nl.cofx.top10.healthcheck;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.OffsetDateTime;

@Slf4j
@RequiredArgsConstructor
public class HealthCheckVerticle extends AbstractVerticle {

    private static final String SELECT_NOW = "SELECT NOW()";

    private final JsonObject jdbcOptions;
    private final Router router;

    private SQLClient sqlClient;

    private String getCommitHash() {
        var commitHash = System.getenv("GIT_COMMIT_HASH");
        if (commitHash == null) return "untracked";


        return commitHash;
    }

    private String getVersion() {
        var version = System.getenv("VERSION");
        if (version == null) return "untracked";

        return version;
    }

    private Future<Instant> getDatabaseTimestamp() {
        return Future.future(promise -> {
            sqlClient.getConnection(asyncConnection -> {
                if (asyncConnection.failed()) {
                    var cause = asyncConnection.cause();
                    log.error("Unable to connect to database", cause);
                    promise.fail(cause);
                    return;
                }

                try (var connection = asyncConnection.result()) {
                    connection.querySingle(SELECT_NOW, asyncTimestamp -> {
                        if (asyncTimestamp.failed()) {
                            var cause = asyncTimestamp.cause();
                            log.error("Unable to execute query \"{}\"", SELECT_NOW, cause);
                            promise.fail(cause);
                            return;
                        }

                        var value = (OffsetDateTime) asyncTimestamp.result().getValue(0);
                        promise.complete(value.toInstant());
                    });
                }
            });
        });
    }

    public void handle(RoutingContext routingContext) {
        var response = routingContext.response();
        response.putHeader("content-type", "application/json");

        getDatabaseTimestamp().onComplete(asyncTimestamp -> {
            if (asyncTimestamp.failed()) {
                response.setStatusCode(500).end(new JsonObject().put("error", "Unable to query database").toBuffer());
                return;
            }

            response.end(new JsonObject()
                    .put("commitHash", getCommitHash())
                    .put("version", getVersion())
                    .put("databaseTimestamp", asyncTimestamp.result())
                    .toBuffer());
        });
    }

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        router.route(HttpMethod.GET, "/health").handler(this::handle);
    }
}
