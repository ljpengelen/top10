package nl.friendlymirror.top10.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.RandomPort;

@Log4j2
@DisplayName("Health-check verticle")
@ExtendWith(VertxExtension.class)
public class HealthCheckVerticleTest {

    private static final String PATH = "/health";

    private final int port = RandomPort.get();

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);
        vertx.deployVerticle(new HealthCheckVerticle(router), deploymentResult -> {
            if (deploymentResult.succeeded()) {
                server.listen(port, vertxTestContext.completing());
            } else {
                var cause = deploymentResult.cause();
                log.error("Failed to deploy verticle", cause);
                vertxTestContext.failNow(cause);
            }
        });
    }

    @Test
    @DisplayName("Returns an HTTP 200 OK on every request")
    public void returnsHttp200Ok(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to health endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(200);
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    @DisplayName("Returns commit hash on every request")
    public void returnsCommitHash(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to health endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.bodyAsJsonObject().getString("commitHash")).isNotBlank();
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    @DisplayName("Returns version on every request")
    public void returnsVersion(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to health endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.bodyAsJsonObject().getString("version")).isNotBlank();
                    });
                    vertxTestContext.completeNow();
                });
    }
}
