package nl.cofx.top10.healthcheck;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.PostgresExtension;
import nl.cofx.top10.RandomPort;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.http.JsonObjectBodyHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("Health-check verticle")
@ExtendWith(PostgresExtension.class)
@ExtendWith(VertxExtension.class)
public class HealthCheckVerticleTest {

    private static final String PATH = "/health";
    private static final TestConfig TEST_CONFIG = new TestConfig();

    private int port;

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer(RandomPort.httpServerOptions());
        var router = Router.router(vertx);

        server.requestHandler(router);

        vertx.deployVerticle(new HealthCheckVerticle(TEST_CONFIG.getJdbcOptions(), router), deploymentResult -> {
            if (deploymentResult.succeeded()) {
                server.listen().onComplete(asyncServer -> {
                    if (asyncServer.failed()) {
                        vertxTestContext.failNow(asyncServer.cause());
                        return;
                    }
                    port = asyncServer.result().actualPort();
                    log.info("Using port {}", port);
                    vertxTestContext.completeNow();
                });
            } else {
                vertxTestContext.failNow(deploymentResult.cause());
            }
        });
    }

    @Test
    @DisplayName("Returns an HTTP 200 OK on every request")
    public void returnsHttp200Ok() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Returns commit hash on every request")
    public void returnsCommitHash() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.body().getString("commitHash")).isNotBlank();
    }

    @Test
    @DisplayName("Returns version on every request")
    public void returnsVersion() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.body().getString("version")).isNotBlank();
    }

    @Test
    @DisplayName("Returns timestamp on every request")
    public void returnsTimestamp() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var before = Instant.now();
        var response = httpClient.send(request, new JsonObjectBodyHandler());
        var after = Instant.now();

        var timestamp = response.body().getInstant("databaseTimestamp");
        assertThat(timestamp).isBetween(before, after);
    }
}
