package nl.friendlymirror.top10.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.ErrorHandlers;
import nl.friendlymirror.top10.RandomPort;
import nl.friendlymirror.top10.http.JsonObjectBodyHandler;

@Log4j2
@ExtendWith(VertxExtension.class)
class QuizHttpVerticleTest {

    private static final int ACCOUNT_ID = 12345;
    private static final String EXTERNAL_ID = "abcdefg";

    private final int port = RandomPort.get();

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);

        router.route().handler(routingContext -> {
            routingContext.setUser(User.create(new JsonObject().put("accountId", ACCOUNT_ID)));
            routingContext.next();
        });

        ErrorHandlers.configure(router);

        vertx.deployVerticle(new QuizHttpVerticle(router), deploymentResult -> {
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
    public void completesQuiz(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        vertx.eventBus().consumer("entity.quiz.complete", request -> {
            vertxTestContext.verify(() -> {
                var body = (JsonObject) request.body();
                assertThat(body.getInteger("accountId")).isEqualTo(ACCOUNT_ID);
                assertThat(body.getString("externalId")).isEqualTo(EXTERNAL_ID);
            });
            request.reply(true);
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/complete"))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(201);
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionByNonCreator(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var errorMessage = "Forbidden to complete";
        vertx.eventBus().consumer("entity.quiz.complete", request -> request.fail(403, errorMessage));

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/complete"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo(errorMessage);
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionOfUnknownQuiz(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var errorMessage = "Not found";
        vertx.eventBus().consumer("entity.quiz.complete", request -> request.fail(404, errorMessage));

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/complete"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(errorMessage);
        vertxTestContext.completeNow();
    }
}
