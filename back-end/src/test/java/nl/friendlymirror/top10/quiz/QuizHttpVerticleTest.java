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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.ErrorHandlers;
import nl.friendlymirror.top10.RandomPort;
import nl.friendlymirror.top10.http.*;

@Log4j2
@ExtendWith(VertxExtension.class)
class QuizHttpVerticleTest {

    private static final int ACCOUNT_ID = 12345;
    private static final String EXTERNAL_ID = "abcdefg";
    private static final Instant DEADLINE = Instant.now();
    private static final int QUIZ_ID = 9876;
    private static final String NAME = "Greatest Hits";

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
    public void returnsAllQuizzes(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quizzes = new JsonArray().add("a").add("b");
        vertx.eventBus().consumer("entity.quiz.getAll", request -> {
            vertxTestContext.verify(() -> assertThat(request.body()).isEqualTo(ACCOUNT_ID));
            request.reply(quizzes);
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        var response = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(quizzes);
        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleQuiz(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject().put("externalId", EXTERNAL_ID);
        vertx.eventBus().consumer("entity.quiz.getOne", request -> {
            vertxTestContext.verify(() -> assertThat(request.body()).isEqualTo(EXTERNAL_ID));
            request.reply(quiz);
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(quiz);
        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingUnknownQuiz(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var errorMessage = "Quiz not found";
        vertx.eventBus().consumer("entity.quiz.getOne", request -> {
            vertxTestContext.verify(() -> assertThat(request.body()).isEqualTo(EXTERNAL_ID));
            request.fail(404, errorMessage);
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(errorMessage);
        vertxTestContext.completeNow();
    }

    @Test
    public void createsQuiz(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject()
                .put("name", NAME)
                .put("deadline", DEADLINE);

        vertx.eventBus().consumer("entity.quiz.create", request -> {
            vertxTestContext.verify(() -> {
                var body = (JsonObject) request.body();
                assertThat(body.getString("name")).isEqualTo(NAME);
                assertThat(body.getInteger("creatorId")).isEqualTo(ACCOUNT_ID);
                var externalId = body.getString("externalId");
                assertThat(externalId).isNotBlank();
                assertThat(externalId).hasSize(32);
                assertThat(body.getInstant("deadline")).isEqualTo(DEADLINE);
            });
            request.reply(QUIZ_ID);
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(quiz))
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(201);
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithoutBody(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Request body is empty");
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithBlankName(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("deadline", DEADLINE)))
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Name is blank");
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithInvalidDeadline(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("name", NAME).put("deadline", "invalid date")))
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Invalid instant provided for property \"deadline\"");
        vertxTestContext.completeNow();
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

    @Test
    public void letsAccountParticipate(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        vertx.eventBus().consumer("entity.quiz.participate", request -> {
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
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/participate"))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(201);
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipationInUnknownQuiz(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var errorMessage = "Not found";
        vertx.eventBus().consumer("entity.quiz.participate", request -> request.fail(404, errorMessage));

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/participate"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(errorMessage);
        vertxTestContext.completeNow();
    }

    @Test
    public void returnsParticipants(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var participants = new JsonArray().add(1).add(2);
        vertx.eventBus().consumer("entity.quiz.participants", request -> {
            vertxTestContext.verify(() -> assertThat(request.body()).isEqualTo(EXTERNAL_ID));
            request.reply(participants);
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/participants"))
                .build();
        var response = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(participants);
        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingParticipantsForUnknownQuiz(Vertx vertx, VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var errorMessage = "Not found";
        vertx.eventBus().consumer("entity.quiz.participants", request -> {
            request.fail(404, errorMessage);
        });

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/participants"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(errorMessage);
        vertxTestContext.completeNow();
    }
}
