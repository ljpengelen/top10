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

@ExtendWith(VertxExtension.class)
@Slf4j
class FailureHandlerTest {

    private int port;
    private Router router;

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        router = Router.router(vertx);
        FailureHandler.configure(router);

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
    void respondsWithInternalServerError_givenRoutingFailureWithException() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/").handler(routingContext ->
                routingContext.fail(new RuntimeException("Something went wrong")));

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body().getString("error")).isEqualTo("Internal server error");
    }

    @Test
    void respondsWithStatusCode_givenRoutingFailureWithStatusCode() throws IOException, InterruptedException {
        router.route(HttpMethod.GET, "/").handler(routingContext ->
                routingContext.fail(418));

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(418);
        assertThat(response.body()).isNull();
    }
}
