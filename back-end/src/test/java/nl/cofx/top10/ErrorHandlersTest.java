package nl.cofx.top10;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.http.JsonObjectBodyHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@ExtendWith(VertxExtension.class)
class ErrorHandlersTest {

    private int port;

    private Router router;

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        router = Router.router(vertx);
        ErrorHandlers.configure(router);

        var server = vertx.createHttpServer(RandomPort.httpServerOptions());
        server.requestHandler(router);

        server.listen().onComplete(asyncServer -> {
            if (asyncServer.failed()) {
                vertxTestContext.failNow(asyncServer.cause());
                return;
            }

            port = asyncServer.result().actualPort();
            log.info("Using port {}", port);
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void handlesForbiddenException() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/forbidden").handler(ar -> {
            throw new ForbiddenException("Forbidden");
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/forbidden"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo("Forbidden");
    }

    @Test
    public void handlesInternalServerError() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/internalServerError").handler(ar -> {
            throw new InternalServerErrorException("Self-thrown internal server error", new RuntimeException("Runtime exception"));
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/internalServerError"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body().getString("error")).isEqualTo("Self-thrown internal server error");
    }

    @Test
    public void handlesInvalidCredentials() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/invalidCredentials").handler(ar -> {
            throw new InvalidCredentialsException("Invalid credentials");
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/invalidCredentials"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body().getString("error")).isEqualTo("Invalid credentials");
    }

    @Test
    public void handlesValidationError() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/validationError").handler(ar -> {
            throw new ValidationException("Validation error");
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/validationError"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Validation error");
    }

    @Test
    public void handlesConflict() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/conflict").handler(ar -> {
            throw new ConflictException("Conflict");
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/conflict"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body().getString("error")).isEqualTo("Conflict");
    }

    @Test
    public void handlesRuntimeException() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/runtime").handler(ar -> {
            throw new RuntimeException("Runtime exception");
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/runtime"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body().getString("error")).isEqualTo("Internal server error");
    }

    @Test
    public void handlesNotFound() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/nonExistingResource"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Resource not found");
    }
}
