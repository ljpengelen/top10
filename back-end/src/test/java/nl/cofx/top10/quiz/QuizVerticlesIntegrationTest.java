package nl.cofx.top10.quiz;

import static nl.cofx.top10.postgresql.PostgreSql.toTimestamptz;
import static nl.cofx.top10.postgresql.PostgreSql.toUuid;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.time.Period;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.*;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.eventbus.MessageCodecs;
import nl.cofx.top10.http.HttpClient;
import nl.cofx.top10.migration.MigrationVerticle;

@Log4j2
@ExtendWith(VertxExtension.class)
class QuizVerticlesIntegrationTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String NON_EXISTING_QUIZ_ID = "f30e86fa-f2ab-4790-ac4b-63a052534510";

    private static final String QUIZ_NAME = "Greatest Hits";
    private static final Instant DEADLINE = Instant.now().plus(Period.ofDays(1));
    private static final String USERNAME_1 = "John Doe";
    private static final String USERNAME_2 = "Jane Doe";
    private static final String EMAIL_ADDRESS_1 = "john.doe@example.com";
    private static final String EMAIL_ADDRESS_2 = "jane.doe@example.com";

    private int port;
    private HttpClient httpClient;
    private final UserHandler userHandler = new UserHandler();

    private String accountId1;
    private String accountId2;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.succeedingThenComplete());

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

        accountId1 = createAccount(connection, USERNAME_1, EMAIL_ADDRESS_1);
        accountId2 = createAccount(connection, USERNAME_2, EMAIL_ADDRESS_2);

        connection.close();
    }

    private String createAccount(Connection connection, String username, String emailAddress) throws SQLException {
        var accountQueryTemplate = "INSERT INTO account (name, email_address, first_login_at, last_login_at) VALUES ('%s', '%s', NOW(), NOW())";
        var query = String.format(accountQueryTemplate, username, emailAddress);
        var statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        return generatedKeys.getString(6).replace("-", "");
    }

    private void deleteQuizzes() throws SQLException {
        var connection = getConnection();
        connection.prepareStatement("TRUNCATE TABLE quiz CASCADE").execute();
        connection.close();
    }

    private void deployVerticles(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer(RandomPort.httpServerOptions());
        var router = Router.router(vertx);
        server.requestHandler(router);

        router.route().handler(userHandler::handle);

        ErrorHandlers.configure(router);
        vertx.deployVerticle(new QuizEntityVerticle(TEST_CONFIG.getJdbcOptions()));
        vertx.deployVerticle(new QuizHttpVerticle(router));
        vertx.deployVerticle(new ListHttpVerticle(router));
        vertx.deployVerticle(new ListEntityVerticle(TEST_CONFIG.getJdbcOptions()));

        server.listen().onComplete(asyncServer -> {
            if (asyncServer.failed()) {
                vertxTestContext.failNow(asyncServer.cause());
                return;
            }

            port = asyncServer.result().actualPort();
            log.info("Using port {}", port);
            httpClient = new HttpClient(port);
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void createsQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var quiz = quiz();
        var response = httpClient.createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(200);
        var body = response.body();
        assertThat(body).isNotNull();
        var quizId = body.getString("id");
        assertThat(quizId).isNotBlank();

        response = httpClient.getQuiz(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        quiz = response.body();
        assertThat(quiz.getString("id")).isNotNull();
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getString("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("personalListId")).isNotNull();
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
        userHandler.logIn(accountId1);

        var response = httpClient.createQuiz(null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Request body is empty");

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithBlankName(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var quiz = new JsonObject().put("deadline", DEADLINE);
        var response = httpClient.createQuiz(quiz);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Name is blank");

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCreationRequestWithInvalidDeadline(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

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
        var quizId = createQuizResponse.body().getString("quizId");

        var response = httpClient.getQuizzes();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(1);
        var quiz = response.body().getJsonObject(0);
        assertThat(quiz.getString("id")).isNotNull();
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getString("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("personalListId")).isNotNull();
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleQuizWhenLoggedIn(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var quizId = createQuizResponse.body().getString("id");
        var response = httpClient.getQuiz(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("id")).isNotNull();
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getString("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isTrue();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("personalListId")).isNotNull();
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleQuizWhenLoggedOut(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        userHandler.logOut();

        var quizId = createQuizResponse.body().getString("id");
        var response = httpClient.getQuiz(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("id")).isNotNull();
        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
        assertThat(quiz.getString("creatorId")).isEqualTo(accountId1);
        assertThat(quiz.getBoolean("isCreator")).isFalse();
        assertThat(quiz.getBoolean("isActive")).isTrue();
        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
        assertThat(quiz.getString("personalListId")).isNull();
        assertThat(quiz.getBoolean("personalListHasDraftStatus")).isNull();

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var response = httpClient.getQuiz(NON_EXISTING_QUIZ_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(String.format("Quiz \"%s\" not found", NON_EXISTING_QUIZ_ID));

        vertxTestContext.completeNow();
    }

    @Test
    public void letsAccountParticipate(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        var quizId = createQuizResponse.body().getString("id");

        userHandler.logIn(accountId2);
        var response = httpClient.participateInQuiz(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getString("personalListId")).isNotNull();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipationInUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var response = httpClient.participateInQuiz(NON_EXISTING_QUIZ_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(String.format("Quiz \"%s\" not found", NON_EXISTING_QUIZ_ID));

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsParticipatingTwice(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var quizId = createQuizResponse.body().getString("id");
        var secondResponse = httpClient.participateInQuiz(quizId);

        assertThat(secondResponse.statusCode()).isEqualTo(409);
        assertThat(secondResponse.body().getString("error")).isEqualTo(String.format("Account \"%s\" already has a list for quiz \"%s\"", accountId1, quizId));

        vertxTestContext.completeNow();
    }

    private String createQuiz() throws IOException, InterruptedException {
        var createResponse = httpClient.createQuiz(quiz());
        return createResponse.body().getString("id");
    }

    @Test
    public void returnsParticipants(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var quizId = createQuiz();

        userHandler.logIn(accountId2);
        httpClient.participateInQuiz(quizId);

        var response = httpClient.getParticipants(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var body = new JsonArray(response.body());
        assertThat(body).hasSize(2);

        assertThat(body).anySatisfy(jsonObject -> {
            var participant = (JsonObject) jsonObject;
            assertThat(participant.getString("id")).isEqualTo(accountId1);
            assertThat(participant.getString("name")).isEqualTo(USERNAME_1);
            assertThat(participant.getBoolean("listHasDraftStatus")).isTrue();
            assertThat(participant.getBoolean("isOwnAccount")).isFalse();
            assertThat(participant.getString("assignedListId")).isNull();
        });

        assertThat(body).anySatisfy(jsonObject -> {
            var participant = (JsonObject) jsonObject;
            assertThat(participant.getString("id")).isEqualTo(accountId2);
            assertThat(participant.getString("name")).isEqualTo(USERNAME_2);
            assertThat(participant.getBoolean("listHasDraftStatus")).isTrue();
            assertThat(participant.getBoolean("isOwnAccount")).isTrue();
            assertThat(participant.getString("assignedListId")).isNull();
        });

        vertxTestContext.completeNow();
    }

    private String getList(int position) throws IOException, InterruptedException {
        var listsResponse = httpClient.getLists();
        return listsResponse.body().getJsonObject(position).getString("id");
    }

    @Test
    public void returnsAssignedListsOfParticipants(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var quizId = createQuiz();
        var listId = getList(0);

        httpClient.finalizeList(listId);
        httpClient.assignList(listId, accountId1);

        var response = httpClient.getParticipants(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var body = new JsonArray(response.body());
        assertThat(body).hasSize(1);

        assertThat(body).anySatisfy(jsonObject -> {
            var participant = (JsonObject) jsonObject;
            assertThat(participant.getString("id")).isEqualTo(accountId1);
            assertThat(participant.getString("name")).isEqualTo(USERNAME_1);
            assertThat(participant.getBoolean("listHasDraftStatus")).isFalse();
            assertThat(participant.getBoolean("isOwnAccount")).isTrue();
            var assignedLists = participant.getJsonArray("assignedLists");
            assertThat(assignedLists).hasSize(1);
            assertThat(assignedLists.getString(0)).isEqualTo(listId);
        });

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingParticipantsForUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var response = httpClient.getParticipants(NON_EXISTING_QUIZ_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        var body = new JsonObject(response.body());
        assertThat(body.getString("error")).isEqualTo(String.format("Quiz \"%s\" not found", NON_EXISTING_QUIZ_ID));

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsQuizResults(VertxTestContext vertxTestContext) throws IOException, InterruptedException, SQLException {
        userHandler.logIn(accountId1);

        var createQuizResponse = httpClient.createQuiz(quiz());
        var quizId = createQuizResponse.body().getString("id");
        var listsResponse = httpClient.getLists();
        assertThat(listsResponse.statusCode()).isEqualTo(200);
        var lists = listsResponse.body();
        assertThat(lists).hasSize(1);
        var listId1 = lists.getJsonObject(0).getString("id");
        httpClient.finalizeList(listId1);

        userHandler.logIn(accountId2);

        httpClient.participateInQuiz(quizId);
        listsResponse = httpClient.getLists();
        assertThat(listsResponse.statusCode()).isEqualTo(200);
        lists = listsResponse.body();
        assertThat(lists).hasSize(1);
        var listId2 = lists.getJsonObject(0).getString("id");
        httpClient.finalizeList(listId2);

        updateDeadline(quizId, Instant.now());

        userHandler.logIn(accountId1);

        httpClient.assignList(listId1, accountId1);
        httpClient.assignList(listId2, accountId2);

        userHandler.logIn(accountId1);

        httpClient.completeQuiz(quizId);

        var response = httpClient.getQuizResults(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("quizId")).isEqualTo(quizId);
        var allPersonalResults = quiz.getJsonObject("personalResults");
        assertThat(allPersonalResults).hasSize(2);

        var firstPersonalResults = allPersonalResults.getJsonObject(accountId1);
        assertThat(firstPersonalResults.getString("accountId")).isEqualTo(accountId1);
        assertThat(firstPersonalResults.getString("name")).isEqualTo(USERNAME_1);
        assertThat(firstPersonalResults.getJsonArray("incorrectAssignments")).isEmpty();
        var correctAssignments = firstPersonalResults.getJsonArray("correctAssignments");
        assertThat(correctAssignments).hasSize(1);
        assertThat(correctAssignments).anySatisfy(rawAssignment -> {
            assertThat(rawAssignment).isInstanceOf(JsonObject.class);
            var assignment = (JsonObject) rawAssignment;
            assertThat(assignment.getString("creatorId")).isEqualTo(accountId2);
            assertThat(assignment.getString("creatorName")).isEqualTo(USERNAME_2);
            assertThat(assignment.getString("assigneeId")).isEqualTo(accountId2);
            assertThat(assignment.getString("assigneeName")).isEqualTo(USERNAME_2);
            assertThat(assignment.getString("listId")).isEqualTo(listId2);
        });

        var secondPersonalResults = allPersonalResults.getJsonObject(accountId2);
        assertThat(secondPersonalResults.getString("accountId")).isEqualTo(accountId2);
        assertThat(secondPersonalResults.getString("name")).isEqualTo(USERNAME_2);
        var incorrectAssignments = secondPersonalResults.getJsonArray("incorrectAssignments");
        assertThat(incorrectAssignments).hasSize(1);
        assertThat(incorrectAssignments).anySatisfy(rawAssignment -> {
            assertThat(rawAssignment).isInstanceOf(JsonObject.class);
            var assignment = (JsonObject) rawAssignment;
            assertThat(assignment.getString("creatorId")).isEqualTo(accountId1);
            assertThat(assignment.getString("creatorName")).isEqualTo(USERNAME_1);
            assertThat(assignment.getString("assigneeId")).isNull();
            assertThat(assignment.getString("assigneeName")).isNull();
            assertThat(assignment.getString("listId")).isEqualTo(listId1);
        });
        assertThat(secondPersonalResults.getJsonArray("correctAssignments")).isEmpty();

        var ranking = quiz.getJsonArray("ranking");
        assertThat(ranking).hasSize(2);
        var rankEntryForJane = ranking.getJsonObject(0);
        assertThat(rankEntryForJane.getInteger("rank")).isEqualTo(1);
        assertThat(rankEntryForJane.getString("accountId")).isEqualTo(accountId1);
        assertThat(rankEntryForJane.getString("name")).isEqualTo(USERNAME_1);
        assertThat(rankEntryForJane.getInteger("numberOfCorrectAssignments")).isEqualTo(1);
        var rankEntryForJohn = ranking.getJsonObject(1);
        assertThat(rankEntryForJohn.getInteger("rank")).isEqualTo(2);
        assertThat(rankEntryForJohn.getString("accountId")).isEqualTo(accountId2);
        assertThat(rankEntryForJohn.getString("name")).isEqualTo(USERNAME_2);
        assertThat(rankEntryForJohn.getInteger("numberOfCorrectAssignments")).isEqualTo(0);

        vertxTestContext.completeNow();
    }

    private void updateDeadline(String quizId, Instant now) throws SQLException {
        var connection = getConnection();

        var statement = connection.prepareStatement("UPDATE quiz SET deadline = ? WHERE quiz_id = ?");
        statement.setObject(1, toTimestamptz(now));
        statement.setObject(2, toUuid(quizId));
        statement.execute();

        connection.close();
    }

    @Test
    public void returnsEmptyQuizResultsForQuizWithoutAssignments(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        var quizId = createQuizResponse.body().getString("id");
        httpClient.completeQuiz(quizId);

        var response = httpClient.getQuizResults(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        var quiz = response.body();
        assertThat(quiz.getString("quizId")).isEqualTo(quizId);
        assertThat(quiz.getJsonObject("personalResults")).isEmpty();

        vertxTestContext.completeNow();
    }

    @Test
    public void returns403WhenRequestingResultsForActiveQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var quizId = createQuizResponse.body().getString("id");
        var response = httpClient.getQuizResults(quizId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo(String.format("Quiz \"%s\" is still active", quizId));

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingResultsForUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var response = httpClient.getQuizResults(NON_EXISTING_QUIZ_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(String.format("Quiz \"%s\" not found", NON_EXISTING_QUIZ_ID));

        vertxTestContext.completeNow();
    }

    @Test
    public void completesQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());

        var quizId = createQuizResponse.body().getString("id");
        var completeResponse = httpClient.completeQuiz(quizId);

        assertThat(completeResponse.statusCode()).isEqualTo(204);

        var getResponse = httpClient.getQuiz(quizId);

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isNotNull();
        assertThat(getResponse.body().getBoolean("isActive")).isFalse();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionByNonCreator(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var createQuizResponse = httpClient.createQuiz(quiz());
        var quizId = createQuizResponse.body().getString("id");

        userHandler.logIn(accountId2);
        var response = httpClient.completeQuiz(quizId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo("Account \"" + accountId2 + "\" is not allowed to close quiz \"" + quizId + "\"");

        response = httpClient.getQuiz(quizId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isNotNull();
        assertThat(response.body().getBoolean("isActive")).isTrue();

        vertxTestContext.completeNow();
    }

    @Test
    public void rejectsCompletionOfUnknownQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);

        var response = httpClient.completeQuiz(NON_EXISTING_QUIZ_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().getString("error")).isEqualTo(String.format("Quiz \"%s\" not found", NON_EXISTING_QUIZ_ID));

        vertxTestContext.completeNow();
    }
}
