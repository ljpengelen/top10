package nl.friendlymirror.top10.healthcheck;

import java.io.IOException;
import java.util.Properties;

import com.google.common.annotations.VisibleForTesting;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class HealthCheckVerticle extends AbstractVerticle {

    private final int port;

    private String responseBody;

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
        var properties = new Properties();
        try {
            properties.load(this.getClass().getClassLoader().getResourceAsStream("build.properties"));
            return properties.getProperty("project.version");
        } catch (IOException e) {
            return "unknown";
        }
    }

    @Override
    public void start() {
        log.info("Starting health-check verticle");

        var server = vertx.createHttpServer();

        var commitHash = getCommitHash();
        var version = getVersion();

        responseBody = new JsonObject()
                .put("commitHash", commitHash)
                .put("version", version)
                .toString();

        server.requestHandler(request -> {
            var response = request.response();
            response.putHeader("content-type", "application/json");
            response.end(responseBody);
        });

        server.listen(port, ar -> {
            if (ar.succeeded()) {
                log.info("listening on port {}", port);
            } else {
                log.error("Unable to listen on port {}", port, ar.cause());
            }
        });
    }
}
