package nl.friendlymirror.top10.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
@DisplayName("Health-check verticle")
@ExtendWith(VertxExtension.class)
public class HealthCheckVerticleTest {

    private int port;

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) throws IOException {
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        vertx.deployVerticle(new HealthCheckVerticle(port), vertxTestContext.completing());
    }

    @Test
    @DisplayName("Returns an HTTP 200 OK on every request")
    public void testHTTP200OK(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", "/health")
                .send(ar -> {
                    if (ar.failed()) {
                        log.error("Request to health endpoint failed", ar.cause());
                        vertxTestContext.failNow(ar.cause());
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
        client.get(port, "localhost", "/health")
                .send(ar -> {
                    if (ar.failed()) {
                        log.error("Request to health endpoint failed", ar.cause());
                        vertxTestContext.failNow(ar.cause());
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
        client.get(port, "localhost", "/health")
                .send(ar -> {
                    if (ar.failed()) {
                        log.error("Request to health endpoint failed", ar.cause());
                        vertxTestContext.failNow(ar.cause());
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
