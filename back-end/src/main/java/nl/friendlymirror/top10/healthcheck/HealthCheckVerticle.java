package nl.friendlymirror.top10.healthcheck;

import java.io.IOException;
import java.util.Properties;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class HealthCheckVerticle extends AbstractVerticle {

    private final Buffer responseBody = new JsonObject()
            .put("commitHash", getCommitHash())
            .put("version", getVersion())
            .toBuffer();

    private final Router router;

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

    public void handle(RoutingContext routingContext) {
        routingContext.response()
                .putHeader("content-type", "application/json")
                .end(responseBody);
    }

    @Override
    public void start() {
        log.info("Starting");
        router.route(HttpMethod.GET, "/health").handler(this::handle);
    }
}
