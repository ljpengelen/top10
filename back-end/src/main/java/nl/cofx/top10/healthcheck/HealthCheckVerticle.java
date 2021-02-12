package nl.cofx.top10.healthcheck;

import java.time.Instant;
import java.util.Properties;

import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class HealthCheckVerticle extends AbstractVerticle {

    private static final String SELECT_NOW = "SELECT NOW()";
    private final JsonObject jdbcOptions;
    private final Router router;

    private SQLClient sqlClient;

    private String getCommitHash() {
        var commitHash = System.getenv("DEPLOY_REVISION");
        if (commitHash == null) {
            return "untracked";
        }

        return commitHash;
    }

    private String getVersion() {
        var properties = new Properties();
        try {
            properties.load(this.getClass().getClassLoader().getResourceAsStream("build.properties"));
            return properties.getProperty("project.version");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private Future<Instant> getDatabaseTimestamp() {
        var promise = Promise.<Instant> promise();

        sqlClient.getConnection(asyncConnection -> {
            if (asyncConnection.failed()) {
                var cause = asyncConnection.cause();
                log.error("Unable to connect to database", cause);
                promise.fail(cause);
                return;
            }

            asyncConnection.result().querySingle(SELECT_NOW, asyncTimestamp -> {
                if (asyncTimestamp.failed()) {
                    var cause = asyncTimestamp.cause();
                    log.error("Unable to execute query \"{}\"", SELECT_NOW, cause);
                    promise.fail(cause);
                    return;
                }

                promise.complete(asyncTimestamp.result().getInstant(0));
            });
        });

        return promise.future();
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
