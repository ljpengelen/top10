package nl.cofx.top10.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.cofx.top10.*;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.eventbus.MessageCodecs;
import nl.cofx.top10.http.HttpClient;
import nl.cofx.top10.migration.MigrationVerticle;

@ExtendWith(VertxExtension.class)
class QuizVerticlesIntegrationTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

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
    private final HttpClient httpClient = new HttpClient(port);
    private final UserHandler userHandler = new UserHandler();

    private int accountId1;
    private int accountId2;

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
        deleteQuizzes();
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

    private void deleteQuizzes() throws SQLException {
        var connection = getConnection();
        connection.prepareStatement("TRUNCATE TABLE quiz CASCADE").execute();
        connection.close();
    }

    private void deployVerticles(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);

        router.route().handler(userHandler::handle);

        ErrorHandlers.configure(router);
        vertx.deployVerticle(new QuizEntityVerticle(TEST_CONFIG.getJdbcOptions()));
        vertx.deployVerticle(new QuizHttpVerticle(router));
        vertx.deployVerticle(new ListHttpVerticle(router));
        vertx.deployVerticle(new ListEntityVerticle(TEST_CONFIG.getJdbcOptions()));
        vertxTestContext.completeNow();
        server.listen(port);
    }

    @Test
    public void createsQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var quiz = quiz();
        var response = httpClient.createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(200);
        var body = response.body();
        assertThat(body).isNotNull();
        var externalId = body.getString("externalId");
        assertThat(externalId).isNotBlank();

        response = httpClient.getQuiz(externalId);

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

    private static JsonObject quiz() {
        return new JsonObject()
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE);
    }

    @Test
    public void rejectsCreationRequestWithoutBody(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = httpClient.createQuiz(null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Request body is empty");

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithBlankName(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject().put("deadline", DEADLINE);
        var response = httpClient.createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Name is blank");

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithInvalidDeadline(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quiz = new JsonObject().put("name", QUIZ_NAME).put("deadline", "invalid date");
        var response = httpClient.createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Invalid instant provided for property \"deadline\"");

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsAllQuizzes(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        var externalQuizId = createQuizResponse.body().getString("externalId");

        var response = httpClient.getQuizzes();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(1);
        var quiz = response.body().getJsonObject(0);
        assertThat(quiz.getInteger("id")).isNotNull();
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getInteger("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("externalId")).isEqualTo(externalQuizId);
        assertThat(quiz.getInteger("personalListId")).isNotNull();
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var quizId = createQuizResponse.body().getInteger("id");
        var externalQuizId = createQuizResponse.body().getString("externalId");
        var response = httpClient.getQuiz(externalQuizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getInteger("id")).isNotNull();
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getInteger("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("externalId")).isEqualTo(externalQuizId);
        assertThat(quiz.getInteger("personalListId")).isNotNull();
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = httpClient.getQuiz(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }

    @Test
    public void letsAccountParticipate(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        var externalQuizId = createQuizResponse.body().getString("externalId");

        userHandler.logIn(accountId2);
        var response = httpClient.participateInQuiz(externalQuizId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getInteger("personalListId")).isNotNull();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipationInUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = httpClient.participateInQuiz(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");
        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipatingTwice(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var externalQuizId = createQuizResponse.body().getString("externalId");
        var secondResponse = httpClient.participateInQuiz(externalQuizId);

        assertThat(secondResponse.statusCode()).isEqualTo(409);
        assertThat(secondResponse.body().getString("error"))
                .isEqualTo(String.format("Account with ID \"%d\" already has a list for quiz with external ID \"%s\"", accountId1, externalQuizId));

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsParticipants(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createResponse = httpClient.createQuiz(quiz());

        assertThat(createResponse.statusCode()).isEqualTo(200);

        var externalQuizId = createResponse.body().getString("externalId");

        var response = httpClient.getParticipants(externalQuizId);

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
        var response = httpClient.getParticipants(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        var body = new JsonObject(response.body());
        assertThat(body.getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsQuizResults(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var createQuizResponse = httpClient.createQuiz(quiz());
        var externalQuizId = createQuizResponse.body().getString("externalId");
        var listsResponse = httpClient.getLists();
        assertThat(listsResponse.statusCode()).isEqualTo(200);
        var lists = listsResponse.body();
        assertThat(lists).hasSize(1);
        var listId1 = lists.getJsonObject(0).getInteger("id");
        httpClient.finalizeList(listId1);

        userHandler.logIn(accountId2);

        httpClient.participateInQuiz(externalQuizId);
        listsResponse = httpClient.getLists();
        assertThat(listsResponse.statusCode()).isEqualTo(200);
        lists = listsResponse.body();
        assertThat(lists).hasSize(1);
        var listId2 = lists.getJsonObject(0).getInteger("id");
        httpClient.finalizeList(listId2);

        userHandler.logIn(accountId1);

        httpClient.assignList(listId1, EXTERNAL_ACCOUNT_ID_1);
        httpClient.assignList(listId2, EXTERNAL_ACCOUNT_ID_2);

        userHandler.logIn(accountId2);

        httpClient.assignList(listId1, EXTERNAL_ACCOUNT_ID_1);
        httpClient.assignList(listId2, EXTERNAL_ACCOUNT_ID_2);

        userHandler.logIn(accountId1);

        httpClient.completeQuiz(externalQuizId);

        var response = httpClient.getQuizResults(externalQuizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("quizId")).isEqualTo(externalQuizId);
        var allPersonalResults = quiz.getJsonObject("personalResults");
        assertThat(allPersonalResults).hasSize(2);

        var firstPersonalResults = allPersonalResults.getJsonObject(EXTERNAL_ACCOUNT_ID_1);
        assertThat(firstPersonalResults.getString("externalAccountId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(firstPersonalResults.getString("name")).isEqualTo(USERNAME_1);
        assertThat(firstPersonalResults.getJsonArray("incorrectAssignments")).isEmpty();
        var correctAssignments = firstPersonalResults.getJsonArray("correctAssignments");
        assertThat(correctAssignments).hasSize(2);
        var firstAssignment = correctAssignments.getJsonObject(0);
        assertThat(firstAssignment.getString("externalCreatorId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(firstAssignment.getString("creatorName")).isEqualTo(USERNAME_1);
        assertThat(firstAssignment.getString("externalAssigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(firstAssignment.getString("assigneeName")).isEqualTo(USERNAME_1);
        assertThat(firstAssignment.getInteger("listId")).isEqualTo(listId1);
        var secondAssignment = correctAssignments.getJsonObject(1);
        assertThat(secondAssignment.getString("externalCreatorId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(secondAssignment.getString("creatorName")).isEqualTo(USERNAME_2);
        assertThat(secondAssignment.getString("externalAssigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(secondAssignment.getString("assigneeName")).isEqualTo(USERNAME_2);
        assertThat(secondAssignment.getInteger("listId")).isEqualTo(listId2);

        var secondPersonalResults = allPersonalResults.getJsonObject(EXTERNAL_ACCOUNT_ID_2);
        assertThat(secondPersonalResults.getString("externalAccountId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(secondPersonalResults.getString("name")).isEqualTo(USERNAME_2);
        assertThat(secondPersonalResults.getJsonArray("incorrectAssignments")).isEmpty();
        correctAssignments = secondPersonalResults.getJsonArray("correctAssignments");
        assertThat(correctAssignments).hasSize(2);
        firstAssignment = correctAssignments.getJsonObject(0);
        assertThat(firstAssignment.getString("externalCreatorId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(firstAssignment.getString("creatorName")).isEqualTo(USERNAME_1);
        assertThat(firstAssignment.getString("externalAssigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(firstAssignment.getString("assigneeName")).isEqualTo(USERNAME_1);
        assertThat(firstAssignment.getInteger("listId")).isEqualTo(listId1);
        secondAssignment = correctAssignments.getJsonObject(1);
        assertThat(secondAssignment.getString("externalCreatorId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(secondAssignment.getString("creatorName")).isEqualTo(USERNAME_2);
        assertThat(secondAssignment.getString("externalAssigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(secondAssignment.getString("assigneeName")).isEqualTo(USERNAME_2);
        assertThat(secondAssignment.getInteger("listId")).isEqualTo(listId2);

        var ranking = quiz.getJsonArray("ranking");
        assertThat(ranking).hasSize(2);
        var rankEntryForJane = ranking.getJsonObject(0);
        assertThat(rankEntryForJane.getInteger("rank")).isEqualTo(1);
        assertThat(rankEntryForJane.getString("externalAccountId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(rankEntryForJane.getString("name")).isEqualTo(USERNAME_2);
        assertThat(rankEntryForJane.getInteger("numberOfCorrectAssignments")).isEqualTo(2);
        var rankEntryForJohn = ranking.getJsonObject(1);
        assertThat(rankEntryForJohn.getInteger("rank")).isEqualTo(1);
        assertThat(rankEntryForJohn.getString("externalAccountId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(rankEntryForJohn.getString("name")).isEqualTo(USERNAME_1);
        assertThat(rankEntryForJohn.getInteger("numberOfCorrectAssignments")).isEqualTo(2);

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsEmptyQuizResultsForQuizWithoutAssignments(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        var externalQuizId = createQuizResponse.body().getString("externalId");
        httpClient.completeQuiz(externalQuizId);

        var response = httpClient.getQuizResults(externalQuizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("quizId")).isEqualTo(externalQuizId);
        assertThat(quiz.getJsonObject("personalResults")).isEmpty();

        vertxTestContext.completeNow();
    }

    @Test
    public void returns403WhenRequestingResultsForActiveQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var externalQuizId = createQuizResponse.body().getString("externalId");
        var response = httpClient.getQuizResults(externalQuizId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo(String.format("Quiz with external ID \"%s\" is still active", externalQuizId));

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingResultsForUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = httpClient.getQuizResults(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }

    @Test
    public void completesQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var externalQuizId = createQuizResponse.body().getString("externalId");
        var completeResponse = httpClient.completeQuiz(externalQuizId);

        assertThat(completeResponse.statusCode()).isEqualTo(204);

        var getResponse = httpClient.getQuiz(externalQuizId);

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isNotNull();
        assertThat(getResponse.body().getBoolean("isActive")).isFalse();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionByNonCreator(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        var externalId = createQuizResponse.body().getString("externalId");

        userHandler.logIn(accountId2);
        var response = httpClient.completeQuiz(externalId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo("Account \"" + accountId2 + "\" is not allowed to close quiz with external ID \"" + externalId + "\"");

        response = httpClient.getQuiz(externalId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isNotNull();
        assertThat(response.body().getBoolean("isActive")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionOfUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var response = httpClient.completeQuiz(NON_EXISTING_EXTERNAL_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");

        vertxTestContext.completeNow();
    }
}
