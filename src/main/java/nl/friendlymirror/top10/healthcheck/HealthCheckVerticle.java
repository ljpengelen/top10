package nl.friendlymirror.top10.healthcheck;

import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

public class HealthCheckVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger();

    private final int port;

    private String commitHash;
    private String responseBody;
    private HttpServer server;
    private String version;

    @VisibleForTesting HealthCheckVerticle(int port) {
        this.port = port;
    }

    public HealthCheckVerticle() {
        this(1242);
    }

    private String getCommitHash() {
        var commitHash = System.getenv("DEPLOY_REVISION");
        if (commitHash == null) {
            return "untracked";
        }

        return commitHash;
    }

    private String getVersion() {
        Properties properties = new Properties();
        try {
            properties.load(this.getClass().getClassLoader().getResourceAsStream("build.properties"));
            return properties.getProperty("project.version");
        } catch (IOException e) {
            return "unknown";
        }
    }

    @Override
    public void start() {
        LOGGER.info("Starting health-check verticle");

        server = vertx.createHttpServer();

        commitHash = getCommitHash();
        version = getVersion();

        responseBody = new JsonObject()
                .put("commitHash", commitHash)
                .put("version", version)
                .toString();

        server.requestHandler(request -> {
            HttpServerResponse response = request.response();
            response.putHeader("content-type", "application/json");
            response.end(responseBody);
        });

        server.listen(port, ar -> {
            if (ar.succeeded()) {
                LOGGER.info("listening on port {}", port);
            } else {
                LOGGER.error("Unable to listen on port {}", port, ar.cause());
            }
        });
    }
}
