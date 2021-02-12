package nl.cofx.top10.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.RandomPort;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.http.JsonObjectBodyHandler;

@Log4j2
@DisplayName("Health-check verticle")
@ExtendWith(VertxExtension.class)
public class HealthCheckVerticleTest {

    private static final String PATH = "/health";
    private static final TestConfig TEST_CONFIG = new TestConfig();

    private final int port = RandomPort.get();

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);
        vertx.deployVerticle(new HealthCheckVerticle(TEST_CONFIG.getJdbcOptions(), router), deploymentResult -> {
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
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        var timestamp = response.body().getInstant("databaseTimestamp");
        assertThat(timestamp).isNotNull();
        var twoSecondFromNow = Instant.now().plus(Duration.ofSeconds(2));
        assertThat(timestamp).isBefore(twoSecondFromNow);
    }
}
