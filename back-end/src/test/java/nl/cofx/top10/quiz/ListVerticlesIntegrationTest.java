package nl.cofx.top10.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.time.Period;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.*;
import nl.cofx.top10.migration.MigrationVerticle;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.eventbus.MessageCodecs;
import nl.cofx.top10.http.HttpClient;

@Log4j2
@ExtendWith(VertxExtension.class)
class ListVerticlesIntegrationTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String USERNAME_1 = "John Doe";
    private static final String USERNAME_2 = "Jane Doe";
    private static final String EMAIL_ADDRESS_1 = "john.doe@example.com";
    private static final String EMAIL_ADDRESS_2 = "jane.doe@example.org";
    private static final String EXTERNAL_ACCOUNT_ID_1 = "123456789";
    private static final String EXTERNAL_ACCOUNT_ID_2 = "987654321";

    private static final String QUIZ_NAME = "Greatest Hits";
    private static final Instant NOW = Instant.now();
    private static final Instant ONE_WEEK_FROM_NOW = Instant.now().plus(Period.ofDays(7));
    private static final String URL_1 = "https://www.youtube.com/watch?v=RBgcN9lrZ3g&list=PLsn6N7S-aJO3KeJnHmiT3rUcmZqesaj_b&index=9";
    private static final String URL_2 = "https://www.youtube.com/watch?v=FAkj8KiHxjg";
    private static final String EMBEDDABLE_URL_1 = "https://www.youtube-nocookie.com/embed/RBgcN9lrZ3g";
    private static final String EMBEDDABLE_URL_2 = "https://www.youtube-nocookie.com/embed/FAkj8KiHxjg";

    private int port;
    private HttpClient httpClient;
    private final UserHandler userHandler = new UserHandler();

    private int accountId1;
    private int accountId2;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        MessageCodecs.register(vertx.eventBus());

        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.succeedingThenComplete());
    }

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) throws SQLException {
        setUpAccounts();
        deleteQuizzes();
        deployVerticles(vertx, vertxTestContext);
        userHandler.logIn(accountId1);
    }

    private void setUpAccounts() throws SQLException {
        var connection = getConnection();
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();

        accountId1 = createUser(connection, USERNAME_1, EMAIL_ADDRESS_1, EXTERNAL_ACCOUNT_ID_1);
        accountId2 = createUser(connection, USERNAME_2, EMAIL_ADDRESS_2, EXTERNAL_ACCOUNT_ID_2);

        connection.close();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
    }

    private int createUser(Connection connection, String username, String emailAddress, String externalId) throws SQLException {
        var accountQueryTemplate = "INSERT INTO account (name, email_address, first_login_at, last_login_at, external_id) VALUES ('%s', '%s', NOW(), NOW(), %s)";
        var query = String.format(accountQueryTemplate, username, emailAddress, externalId);
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

    private static JsonObject quiz() {
        return new JsonObject()
                .put("name", QUIZ_NAME)
                .put("deadline", NOW);
    }

    private void deployVerticles(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer(RandomPort.httpServerOptions());
        var router = Router.router(vertx);
        server.requestHandler(router);

        router.route().handler(userHandler::handle);

        ErrorHandlers.configure(router);

        vertx.deployVerticle(new QuizHttpVerticle(router));
        vertx.deployVerticle(new QuizEntityVerticle(TEST_CONFIG.getJdbcOptions()));
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

    private String createQuiz() throws IOException, InterruptedException {
        var createQuizResponse = httpClient.createQuiz(quiz());
        return createQuizResponse.body().getString("externalId");
    }

    private int createList() throws IOException, InterruptedException {
        var listsResponse = httpClient.getLists();
        return listsResponse.body().getJsonObject(0).getInteger("id");
    }

    @Test
    public void returnsAllFinalizedListsForQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId1);
        var externalQuizId = createQuiz();
        var listId = createList();
        userHandler.logIn(accountId2);
        httpClient.participateInQuiz(externalQuizId);
        userHandler.logIn(accountId1);

        var listsForQuiz = httpClient.getLists(externalQuizId);

        assertThat(listsForQuiz.statusCode()).isEqualTo(200);
        assertThat(listsForQuiz.body()).isEmpty();

        var finalizeResponse = httpClient.finalizeList(listId);

        assertThat(finalizeResponse.statusCode()).isEqualTo(204);

        listsForQuiz = httpClient.getLists(externalQuizId);

        assertThat(listsForQuiz.statusCode()).isEqualTo(200);
        assertThat(listsForQuiz.body()).isNotNull();
        assertThat(listsForQuiz.body()).hasSize(1);
        var list = listsForQuiz.body().getJsonObject(0);
        assertThat(list.getInteger("id")).isEqualTo(listId);
        assertThat(list.getInteger("assigneeId")).isNull();

        var assignResponse = httpClient.assignList(listId, EXTERNAL_ACCOUNT_ID_2);

        assertThat(assignResponse.statusCode()).isEqualTo(204);

        listsForQuiz = httpClient.getLists(externalQuizId);

        assertThat(listsForQuiz.statusCode()).isEqualTo(200);
        assertThat(listsForQuiz.body()).isNotNull();
        assertThat(listsForQuiz.body()).hasSize(1);
        list = listsForQuiz.body().getJsonObject(0);
        assertThat(list.getInteger("id")).isEqualTo(listId);
        assertThat(list.getString("externalAssigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(list.getString("assigneeName")).isEqualTo(USERNAME_2);

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsAllListsForAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();

        var response = httpClient.getLists();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(1);
        var list = response.body().getJsonObject(0);
        assertThat(list.getInteger("id")).isEqualTo(listId);

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleListForActiveQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        var addVideoResponse = httpClient.addVideo(listId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);

        var getListResponse = httpClient.getList(listId);

        assertThat(getListResponse.statusCode()).isEqualTo(200);
        var body = getListResponse.body();
        assertThat(body.getInteger("id")).isEqualTo(listId);
        assertThat(body.getBoolean("hasDraftStatus")).isTrue();
        assertThat(body.getString("externalQuizId")).isEqualTo(externalQuizId);
        assertThat(body.getBoolean("isActiveQuiz")).isTrue();
        assertThat(body.getInteger("creatorId")).isNull();
        assertThat(body.getString("creatorName")).isNull();
        assertThat(body.getString("externalAssigneeId")).isNull();
        assertThat(body.getString("assigneeName")).isNull();

        var videos = body.getJsonArray("videos");
        assertThat(videos).hasSize(1);
        var video = videos.getJsonObject(0);
        assertThat(video.getInteger("id")).isNotNull();
        assertThat(video.getString("url")).isEqualTo(EMBEDDABLE_URL_1);

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleListForCompletedQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        httpClient.addVideo(listId, URL_1);
        httpClient.completeQuiz(externalQuizId);

        var getListResponse = httpClient.getList(listId);

        assertThat(getListResponse.statusCode()).isEqualTo(200);
        var body = getListResponse.body();
        assertThat(body.getInteger("id")).isEqualTo(listId);
        assertThat(body.getBoolean("hasDraftStatus")).isTrue();
        assertThat(body.getString("externalQuizId")).isEqualTo(externalQuizId);
        assertThat(body.getBoolean("isActiveQuiz")).isFalse();
        assertThat(body.getInteger("creatorId")).isEqualTo(accountId1);
        assertThat(body.getString("creatorName")).isEqualTo(USERNAME_1);
        assertThat(body.getString("externalAssigneeId")).isNull();
        assertThat(body.getString("assigneeName")).isNull();

        var videos = body.getJsonArray("videos");
        assertThat(videos).hasSize(1);
        var video = videos.getJsonObject(0);
        assertThat(video.getInteger("id")).isNotNull();
        assertThat(video.getString("url")).isEqualTo(EMBEDDABLE_URL_1);

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingUnknownList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();
        var nonExistingListId = listId - 1;

        var getListResponse = httpClient.getList(nonExistingListId);

        assertThat(getListResponse.statusCode()).isEqualTo(404);
        assertThat(getListResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleListForSameQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        userHandler.logIn(accountId2);

        var participateRequest = httpClient.participateInQuiz(externalQuizId);
        assertThat(participateRequest.statusCode()).isEqualTo(200);
        var newListId = participateRequest.body().getInteger("personalListId");

        userHandler.logIn(accountId1);

        var response = httpClient.getList(newListId);

        assertThat(response.statusCode()).isEqualTo(200);
        var body = response.body();
        assertThat(body.getInteger("id")).isEqualTo(newListId);
        assertThat(body.getBoolean("hasDraftStatus")).isTrue();
        assertThat(body.getString("externalQuizId")).isEqualTo(externalQuizId);
        assertThat(body.getBoolean("isActiveQuiz")).isTrue();
        assertThat(body.getInteger("creatorId")).isNull();
        assertThat(body.getString("creatorName")).isNull();
        assertThat(body.getString("externalAssigneeId")).isNull();
        assertThat(body.getString("assigneeName")).isNull();
        assertThat(body.getJsonArray("videos")).isEmpty();

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsOnlyOwnListBeforeDeadline(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var quizAfterDeadline = quiz().put("deadline", NOW);
        var quizBeforeDeadline = quiz().put("deadline", ONE_WEEK_FROM_NOW);
        var quizAfterDeadlineId = httpClient.createQuiz(quizAfterDeadline).body().getString("externalId");
        var quizBeforeDeadlineId = httpClient.createQuiz(quizBeforeDeadline).body().getString("externalId");

        userHandler.logIn(accountId2);

        var listAfterDeadlineId = httpClient.participateInQuiz(quizAfterDeadlineId).body().getInteger("personalListId");
        var listBeforeDeadlineId = httpClient.participateInQuiz(quizBeforeDeadlineId).body().getInteger("personalListId");

        userHandler.logIn(accountId1);

        var response = httpClient.getList(listAfterDeadlineId);

        assertThat(response.statusCode()).isEqualTo(200);

        response = httpClient.getList(listBeforeDeadlineId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body().getString("error")).isEqualTo(String.format("Account \"%d\" cannot access list \"%d\"", accountId1, listBeforeDeadlineId));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotReturnListForOtherQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        userHandler.logIn(accountId2);

        httpClient.createQuiz(quiz());
        var listsResponse = httpClient.getLists();
        var lists = listsResponse.body();
        assertThat(lists).hasSize(1);
        var newListId = lists.getJsonObject(0).getInteger("id");

        userHandler.logIn(accountId1);

        var getListResponse = httpClient.getList(newListId);

        assertThat(getListResponse.statusCode()).isEqualTo(403);
        var body = getListResponse.body();
        assertThat(body.getString("error")).isEqualTo(String.format("Account \"%d\" cannot access list \"%d\"", accountId1, newListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void addsVideoToOwnList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();

        var addVideoResponse = httpClient.addVideo(listId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        var body = addVideoResponse.body();
        assertThat(body.getInteger("id")).isNotNull();
        assertThat(body.getString("url")).isEqualTo(EMBEDDABLE_URL_1);

        addVideoResponse = httpClient.addVideo(listId, URL_2);

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        body = addVideoResponse.body();
        assertThat(body.getInteger("id")).isNotNull();
        assertThat(body.getString("url")).isEqualTo(EMBEDDABLE_URL_2);

        var listResponse = httpClient.getList(listId);

        assertThat(listResponse.statusCode()).isEqualTo(200);
        var list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId);
        var videos = list.getJsonArray("videos");
        assertThat(videos).hasSize(2);
        var video = videos.getJsonObject(0);
        assertThat(video.getInteger("id")).isNotNull();
        assertThat(video.getString("url")).isEqualTo(EMBEDDABLE_URL_1);
        video = videos.getJsonObject(1);
        assertThat(video.getInteger("id")).isNotNull();
        assertThat(video.getString("url")).isEqualTo(EMBEDDABLE_URL_2);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAddVideoToFinalizedList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        var addVideoResponse = httpClient.addVideo(listId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        var body = addVideoResponse.body();
        assertThat(body.getInteger("id")).isNotNull();
        assertThat(body.getString("url")).isEqualTo(EMBEDDABLE_URL_1);

        httpClient.finalizeList(listId);

        addVideoResponse = httpClient.addVideo(listId, URL_2);

        assertThat(addVideoResponse.statusCode()).isEqualTo(403);
        assertThat(addVideoResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" is finalized", listId));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAddVideoToListForOtherAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        userHandler.logIn(accountId2);

        var addVideoResponse = httpClient.addVideo(listId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(403);
        assertThat(addVideoResponse.body().getString("error")).isEqualTo(String.format("Account \"%d\" did not create list \"%d\"", accountId2, listId));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAddVideoToNonExistingList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();
        var nonExistingListId = listId - 1;

        var addVideoResponse = httpClient.addVideo(nonExistingListId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(404);
        assertThat(addVideoResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void deletesVideoFromOwnList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();

        var addVideoResponse = httpClient.addVideo(listId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        var body = addVideoResponse.body();
        var videoId = body.getInteger("id");

        var deleteVideoResponse = httpClient.deleteVideo(videoId);

        assertThat(deleteVideoResponse.statusCode()).isEqualTo(204);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotDeleteVideoFromFinalizedList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        var addVideoResponse = httpClient.addVideo(listId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        var body = addVideoResponse.body();
        var videoId = body.getInteger("id");

        httpClient.finalizeList(listId);

        var deleteVideoResponse = httpClient.deleteVideo(videoId);

        assertThat(deleteVideoResponse.statusCode()).isEqualTo(403);
        assertThat(deleteVideoResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" is finalized", listId));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotDeleteVideoFromListOfOtherAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();

        var addVideoResponse = httpClient.addVideo(listId, URL_1);

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        var body = addVideoResponse.body();
        var videoId = body.getInteger("id");

        userHandler.logIn(accountId2);

        var deleteVideoResponse = httpClient.deleteVideo(videoId);

        assertThat(deleteVideoResponse.statusCode()).isEqualTo(403);
        assertThat(deleteVideoResponse.body().getString("error")).isEqualTo(String.format("Account \"%d\" did not create list \"%d\"", accountId2, listId));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotDeleteNonExistingVideo(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var nonExistingVideoId = 0;
        var addVideoResponse = httpClient.deleteVideo(nonExistingVideoId);

        assertThat(addVideoResponse.statusCode()).isEqualTo(404);
        assertThat(addVideoResponse.body().getString("error")).isEqualTo(String.format("List for video ID \"%d\" not found", nonExistingVideoId));

        vertxTestContext.completeNow();
    }

    @Test
    public void finalizesListForOwnAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();

        var finalizeResponse = httpClient.finalizeList(listId);

        assertThat(finalizeResponse.statusCode()).isEqualTo(204);

        var listResponse = httpClient.getList(listId);

        assertThat(listResponse.statusCode()).isEqualTo(200);
        var list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId);
        assertThat(list.getJsonArray("videos")).isEmpty();
        assertThat(list.getBoolean("hasDraftStatus")).isFalse();

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotFinalizeListForOtherAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();

        userHandler.logIn(accountId2);

        var finalizeResponse = httpClient.finalizeList(listId);

        assertThat(finalizeResponse.statusCode()).isEqualTo(403);
        assertThat(finalizeResponse.body().getString("error")).isEqualTo(String.format("Account \"%d\" did not create list \"%d\"", accountId2, listId));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotFinalizeNonExistingList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();
        var nonExistingListId = listId - 1;

        var finalizeResponse = httpClient.finalizeList(nonExistingListId);

        assertThat(finalizeResponse.statusCode()).isEqualTo(404);
        assertThat(finalizeResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void assignsToFinalizedList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        userHandler.logIn(accountId2);
        httpClient.participateInQuiz(externalQuizId);
        userHandler.logIn(accountId1);
        httpClient.finalizeList(listId);

        var assignResponse = httpClient.assignList(listId, EXTERNAL_ACCOUNT_ID_2);

        assertThat(assignResponse.statusCode()).isEqualTo(204);

        var listResponse = httpClient.getList(listId);

        assertThat(listResponse.statusCode()).isEqualTo(200);
        var list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId);
        assertThat(list.getString("externalAssigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);
        assertThat(list.getString("assigneeName")).isEqualTo(USERNAME_2);

        assignResponse = httpClient.assignList(listId, EXTERNAL_ACCOUNT_ID_1);

        assertThat(assignResponse.statusCode()).isEqualTo(204);

        listResponse = httpClient.getList(listId);

        assertThat(listResponse.statusCode()).isEqualTo(200);
        list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId);
        assertThat(list.getString("externalAssigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);
        assertThat(list.getString("assigneeName")).isEqualTo(USERNAME_1);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAssignToNonFinalizedList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        userHandler.logIn(accountId2);
        httpClient.participateInQuiz(externalQuizId);
        userHandler.logIn(accountId1);

        var assignResponse = httpClient.assignList(listId, EXTERNAL_ACCOUNT_ID_2);

        assertThat(assignResponse.statusCode()).isEqualTo(403);
        var expectedMessage = String.format("List \"%d\" has not been finalized yet", listId);
        assertThat(assignResponse.body().getString("error")).isEqualTo(expectedMessage);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAssignListToAccountOutsideQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        httpClient.finalizeList(listId);

        var assignResponse = httpClient.assignList(listId, EXTERNAL_ACCOUNT_ID_2);

        assertThat(assignResponse.statusCode()).isEqualTo(403);
        var expectedMessage = String.format("Account with external ID \"%s\" does not participate in quiz with external ID \"%s\"", EXTERNAL_ACCOUNT_ID_2, externalQuizId);
        assertThat(assignResponse.body().getString("error")).isEqualTo(expectedMessage);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAssignToNonExistingList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        createQuiz();
        var listId = createList();
        var nonExistingListId = listId - 1;

        var assignResponse = httpClient.assignList(nonExistingListId, EXTERNAL_ACCOUNT_ID_1);

        assertThat(assignResponse.statusCode()).isEqualTo(404);
        assertThat(assignResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAssignToListInCompletedQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var externalQuizId = createQuiz();
        var listId = createList();

        userHandler.logIn(accountId1);
        httpClient.finalizeList(listId);

        var assignResponse = httpClient.assignList(listId, EXTERNAL_ACCOUNT_ID_1);

        assertThat(assignResponse.statusCode()).isEqualTo(204);

        httpClient.completeQuiz(externalQuizId);

        assignResponse = httpClient.assignList(listId, EXTERNAL_ACCOUNT_ID_1);

        assertThat(assignResponse.statusCode()).isEqualTo(403);
        assertThat(assignResponse.body().getString("error")).isEqualTo(String.format("Quiz with external ID \"%s\" is completed", externalQuizId));

        vertxTestContext.completeNow();
    }
}
