package nl.friendlymirror.top10.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Instant;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.friendlymirror.top10.ErrorHandlers;
import nl.friendlymirror.top10.RandomPort;
import nl.friendlymirror.top10.config.TestConfig;
import nl.friendlymirror.top10.http.*;
import nl.friendlymirror.top10.migration.MigrationVerticle;

@ExtendWith(VertxExtension.class)
class QuizVerticlesIntegrationTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String EXTERNAL_QUIZ_ID = "abcdefg";
    private static final String QUIZ_NAME = "Greatest Hits";
    private static final Instant DEADLINE = Instant.now();
    private static final String NON_EXISTING_EXTERNAL_ID = "pqrstuvw";
    private static final String USERNAME = "John Doe";
    private static final String EMAIL_ADDRESS = "john.doe@example.com";
    private static final String EXTERNAL_ACCOUNT_ID = "123456789";

    private final int port = RandomPort.get();

    private int accountId;
    private int quizId;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.completing());
    }

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) throws SQLException {
        cleanUp();
        setUpAccounts();
        setUpQuiz();
        deployVerticles(vertx, vertxTestContext);
    }

    private void cleanUp() throws SQLException {
        var connection = getConnection();
        var statement = connection.prepareStatement("TRUNCATE TABLE quiz CASCADE");
        statement.execute();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
    }

    private void setUpAccounts() throws SQLException {
        var connection = getConnection();
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();

        var accountQueryTemplate = "INSERT INTO account (name, email_address, first_login_at, last_login_at, external_id) VALUES ('%s', '%s', NOW(), NOW(), %s)";
        var query = String.format(accountQueryTemplate, USERNAME, EMAIL_ADDRESS, EXTERNAL_ACCOUNT_ID);
        statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        accountId = generatedKeys.getInt(1);
    }

    private void setUpQuiz() throws SQLException {
        var connection = getConnection();
        connection.prepareStatement("TRUNCATE TABLE quiz CASCADE").execute();

        quizId = createQuiz(connection, accountId, EXTERNAL_QUIZ_ID);
    }

    private int createQuiz(Connection connection, int creatorId, String externalId) throws SQLException {
        var query = String.format("INSERT INTO quiz (name, is_active, creator_id, deadline, external_id) VALUES ('%s', true, %d, '%s', '%s')",
                QUIZ_NAME, creatorId, DEADLINE, externalId);
        var statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();

        return generatedKeys.getInt(1);
    }

    private void deployVerticles(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);

        router.route().handler(routingContext -> {
            routingContext.setUser(User.create(new JsonObject().put("accountId", accountId)));
            routingContext.next();
        });

        ErrorHandlers.configure(router);
        vertx.deployVerticle(new QuizEntityVerticle(TEST_CONFIG.getJdbcOptions()));
        vertx.deployVerticle(new QuizHttpVerticle(router));
        vertxTestContext.completeNow();
        server.listen(port);
    }

    @Test
    public void letsAccountParticipate(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_QUIZ_ID + "/participate"))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(201);
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipationInUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + NON_EXISTING_EXTERNAL_ID + "/participate"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipatingTwice(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_QUIZ_ID + "/participate"))
                .build();
        var firstResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(firstResponse.statusCode()).isEqualTo(201);

        request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_QUIZ_ID + "/participate"))
                .build();
        var secondResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(secondResponse.statusCode()).isEqualTo(409);
        assertThat(secondResponse.body().getString("error")).isEqualTo(String.format("Account with ID \"%d\" already has a list for quiz with external ID \"abcdefg\"", accountId));

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsParticipants(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject()
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE);

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(quiz))
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        var createResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(createResponse.statusCode()).isEqualTo(200);

        var externalQuizId = createResponse.body().getString("externalId");

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + externalQuizId + "/participants"))
                .build();
        var response = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(1);

        var participant = response.body().getJsonObject(0);
        assertThat(participant.getString("id")).isEqualTo(EXTERNAL_ACCOUNT_ID);
        assertThat(participant.getString("name")).isEqualTo(USERNAME);

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingParticipantsForUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + NON_EXISTING_EXTERNAL_ID + "/participants"))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");
        vertxTestContext.completeNow();
    }
}
