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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.friendlymirror.top10.ErrorHandlers;
import nl.friendlymirror.top10.RandomPort;
import nl.friendlymirror.top10.config.TestConfig;
import nl.friendlymirror.top10.eventbus.MessageCodecs;
import nl.friendlymirror.top10.http.*;
import nl.friendlymirror.top10.migration.MigrationVerticle;

@ExtendWith(VertxExtension.class)
class QuizVerticlesIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String EXTERNAL_ID_FOR_QUIZ_WITH_LIST = "abcdefg";
    private static final String EXTERNAL_ID_FOR_QUIZ_WITHOUT_LIST = "gfedcba";
    private static final String QUIZ_NAME = "Greatest Hits";
    private static final Instant DEADLINE = Instant.now();
    private static final String NON_EXISTING_EXTERNAL_ID = "pqrstuvw";
    private static final String USERNAME_1 = "John Doe";
    private static final String USERNAME_2 = "Jane Doe";
    private static final String EMAIL_ADDRESS_1 = "john.doe@example.com";
    private static final String EMAIL_ADDRESS_2 = "jane.doe@example.com";
    private static final String EXTERNAL_ACCOUNT_ID_1 = "123456789";
    private static final String EXTERNAL_ACCOUNT_ID_2 = "987654321";

    private final int port = RandomPort.get();

    private int accountId1;
    private int accountId2;
    private int quizWithListId;
    private int listId;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.completing());

        MessageCodecs.register(vertx.eventBus());
    }

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) throws SQLException {
        setUpAccounts();
        setUpQuiz();
        deployVerticles(vertx, vertxTestContext);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
    }

    private void setUpAccounts() throws SQLException {
        var connection = getConnection();
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();

        accountId1 = createAccount(connection, USERNAME_1, EMAIL_ADDRESS_1, EXTERNAL_ACCOUNT_ID_1);
        accountId2 = createAccount(connection, USERNAME_2, EMAIL_ADDRESS_2, EXTERNAL_ACCOUNT_ID_2);

        connection.close();
    }

    private int createAccount(Connection connection, String username, String emailAddress, String externalAccountId) throws SQLException {
        var accountQueryTemplate = "INSERT INTO account (name, email_address, first_login_at, last_login_at, external_id) VALUES ('%s', '%s', NOW(), NOW(), %s)";
        var query = String.format(accountQueryTemplate, username, emailAddress, externalAccountId);
        var statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        return generatedKeys.getInt(1);
    }

    private void setUpQuiz() throws SQLException {
        var connection = getConnection();
        connection.prepareStatement("TRUNCATE TABLE quiz CASCADE").execute();

        quizWithListId = createQuiz(connection, accountId1, EXTERNAL_ID_FOR_QUIZ_WITH_LIST);
        listId = createList(connection, accountId1, quizWithListId);

        createQuiz(connection, accountId2, EXTERNAL_ID_FOR_QUIZ_WITHOUT_LIST);

        connection.close();
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

    private int createList(Connection connection, int accountId, int quizId) throws SQLException {
        var listTemplate = "INSERT INTO list (account_id, quiz_id, has_draft_status) VALUES (%d, %d, true)";
        var query = String.format(listTemplate, accountId, quizId);
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
            routingContext.setUser(User.create(new JsonObject().put("accountId", accountId1)));
            routingContext.next();
        });

        ErrorHandlers.configure(router);
        vertx.deployVerticle(new QuizEntityVerticle(TEST_CONFIG.getJdbcOptions()));
        vertx.deployVerticle(new QuizHttpVerticle(router));
        vertxTestContext.completeNow();
        server.listen(port);
    }

    @Test
    public void createsQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject()
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE);
        var response = createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(200);
        var body = response.body();
        assertThat(body).isNotNull();
        var externalId = body.getString("externalId");
        assertThat(externalId).isNotBlank();

        response = getQuiz(externalId);

        assertThat(response.statusCode()).isEqualTo(200);
        quiz = response.body();
        assertThat(quiz.getInteger("id")).isNotNull();
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getInteger("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("externalId")).isEqualTo(externalId);
        assertThat(quiz.getInteger("personalListId")).isNotNull();
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithoutBody(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = createQuiz(null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Request body is empty");

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithBlankName(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject().put("deadline", DEADLINE);
        var response = createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Name is blank");

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithInvalidDeadline(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject().put("name", QUIZ_NAME).put("deadline", "invalid date");
        var response = createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Invalid instant provided for property \"deadline\"");

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsAllQuizzes(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = getQuizzes();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(1);
        var quiz = response.body().getJsonObject(0);
        assertThat(quiz.getInteger("id")).isEqualTo(quizWithListId);
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getInteger("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("externalId")).isEqualTo(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);
        assertThat(quiz.getInteger("personalListId")).isEqualTo(listId);
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = getQuiz(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getInteger("id")).isEqualTo(quizWithListId);
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getInteger("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("externalId")).isEqualTo(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);
        assertThat(quiz.getInteger("personalListId")).isEqualTo(listId);
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = getQuiz(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }

    @Test
    public void letsAccountParticipate(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = participateInQuiz(EXTERNAL_ID_FOR_QUIZ_WITHOUT_LIST);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getInteger("personalListId")).isNotNull();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipationInUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = participateInQuiz(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipatingTwice(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var secondResponse = participateInQuiz(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);

        assertThat(secondResponse.statusCode()).isEqualTo(409);
        assertThat(secondResponse.body().getString("error"))
                .isEqualTo(String.format("Account with ID \"%d\" already has a list for quiz with external ID \"abcdefg\"", accountId1));

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsParticipants(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject()
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE);
        var createResponse = createQuiz(quiz);

        assertThat(createResponse.statusCode()).isEqualTo(200);

        var externalQuizId = createResponse.body().getString("externalId");

        var response = getParticipants(externalQuizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var body = new JsonArray(response.body());
        assertThat(body).hasSize(1);

        var participant = body.getJsonObject(0);
        assertThat(participant.getString("id")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(participant.getString("name")).isEqualTo(USERNAME_1);

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingParticipantsForUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = getParticipants(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        var body = new JsonObject(response.body());
        assertThat(body.getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsQuizResults(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = getQuizResults(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("quizId")).isEqualTo(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsEmptyQuizResultsForQuizWithoutAssignments(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = getQuizResults(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("quizId")).isEqualTo(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);
        assertThat(quiz.getJsonArray("personalResults")).isEmpty();

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingResultsForUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = getQuizResults(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }

    @Test
    public void completesQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var completeResponse = completeQuiz(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);

        assertThat(completeResponse.statusCode()).isEqualTo(201);

        var getResponse = getQuiz(EXTERNAL_ID_FOR_QUIZ_WITH_LIST);

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isNotNull();
        assertThat(getResponse.body().getBoolean("isActive")).isFalse();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionByNonCreator(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = completeQuiz(EXTERNAL_ID_FOR_QUIZ_WITHOUT_LIST);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo("Account \"" + accountId1 + "\" is not allowed to close quiz with external ID \"gfedcba\"");

        response = getQuiz(EXTERNAL_ID_FOR_QUIZ_WITHOUT_LIST);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isNotNull();
        assertThat(response.body().getBoolean("isActive")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionOfUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = completeQuiz(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }

    private HttpResponse<JsonArray> getQuizzes() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        return HTTP_CLIENT.send(request, new JsonArrayBodyHandler());
    }

    private HttpResponse<JsonObject> createQuiz(JsonObject quiz) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(quiz))
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    private HttpResponse<JsonObject> participateInQuiz(String externalId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + externalId + "/participate"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    private HttpResponse<JsonObject> getQuizResults(String externalQuizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + externalQuizId + "/result"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    private HttpResponse<String> getParticipants(String externalQuizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + externalQuizId + "/participants"))
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<JsonObject> completeQuiz(String externalQuizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + externalQuizId + "/complete"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    private HttpResponse<JsonObject> getQuiz(String externalQuizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + externalQuizId))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }
}
