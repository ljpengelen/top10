package nl.cofx.top10;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.http.JsonObjectBodyHandler;
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

    @Test
    void respondsWithInternalServerError(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var router = Router.router(vertx);
        router.route(HttpMethod.GET, "/").handler(routingContext ->
                routingContext.fail(new RuntimeException("Something went wrong")));
        FailureHandler.add(router.getRoutes());

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

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body().getString("error")).isEqualTo("Internal server error");
    }
}
