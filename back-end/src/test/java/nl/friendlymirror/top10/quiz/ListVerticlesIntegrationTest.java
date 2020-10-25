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
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.ErrorHandlers;
import nl.friendlymirror.top10.RandomPort;
import nl.friendlymirror.top10.config.TestConfig;
import nl.friendlymirror.top10.eventbus.MessageCodecs;
import nl.friendlymirror.top10.http.*;
import nl.friendlymirror.top10.migration.MigrationVerticle;

@Log4j2
@ExtendWith(VertxExtension.class)
class ListVerticlesIntegrationTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String USERNAME_1 = "John Doe";
    private static final String USERNAME_2 = "Jane Doe";
    private static final String USERNAME_3 = "Jeff Doe";
    private static final String EMAIL_ADDRESS_1 = "john.doe@example.com";
    private static final String EMAIL_ADDRESS_2 = "jane.doe@example.org";
    private static final String EMAIL_ADDRESS_3 = "jeff.doe@example.co.uk";
    private static final String EXTERNAL_ACCOUNT_ID_1 = "123456789";
    private static final String EXTERNAL_ACCOUNT_ID_2 = "987654321";
    private static final String EXTERNAL_ACCOUNT_ID_3 = "123581321";

    private static final String QUIZ_NAME = "Greatest Hits";
    private static final Instant DEADLINE = Instant.now();
    private static final String EXTERNAL_QUIZ_ID_1 = "abcdefg";
    private static final String EXTERNAL_QUIZ_ID_2 = "pqrstuvw";
    private static final String URL_1 = "https://www.youtube.com/watch?v=RBgcN9lrZ3g&list=PLsn6N7S-aJO3KeJnHmiT3rUcmZqesaj_b&index=9";
    private static final String URL_2 = "https://www.youtube.com/watch?v=FAkj8KiHxjg";
    private static final String EMBEDDABLE_URL_1 = "https://www.youtube-nocookie.com/embed/RBgcN9lrZ3g";
    private static final String EMBEDDABLE_URL_2 = "https://www.youtube-nocookie.com/embed/FAkj8KiHxjg";
    private static final String EMBEDDABLE_URL_3 = "https://www.youtube-nocookie.com/embed/66H4uoJkQ9g";

    private final int port = RandomPort.get();

    private int accountId1;
    private int accountId2;
    private int accountId3;
    private int quizId1;
    private int quizId2;
    private int listId1;
    private int listId2;
    private int listId3;
    private int videoId;

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
        setUpQuizzes();
        deployVerticle(vertx, vertxTestContext);
    }

    private void setUpAccounts() throws SQLException {
        var connection = getConnection();
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();

        accountId1 = createUser(connection, USERNAME_1, EMAIL_ADDRESS_1, EXTERNAL_ACCOUNT_ID_1);
        accountId2 = createUser(connection, USERNAME_2, EMAIL_ADDRESS_2, EXTERNAL_ACCOUNT_ID_2);
        accountId3 = createUser(connection, USERNAME_3, EMAIL_ADDRESS_3, EXTERNAL_ACCOUNT_ID_3);
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

    private void setUpQuizzes() throws SQLException {
        var connection = getConnection();
        connection.prepareStatement("TRUNCATE TABLE quiz CASCADE").execute();

        quizId1 = createQuiz(connection, accountId1, EXTERNAL_QUIZ_ID_1);
        quizId2 = createQuiz(connection, accountId3, EXTERNAL_QUIZ_ID_2);

        listId1 = createList(connection, accountId1, quizId1);
        listId2 = createList(connection, accountId2, quizId1);
        listId3 = createList(connection, accountId3, quizId2);

        videoId = createVideo(connection, listId3, EMBEDDABLE_URL_3);
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

    private int createVideo(Connection connection, int listId, String url) throws SQLException {
        var listTemplate = "INSERT INTO video (list_id, url) VALUES (%d, '%s')";
        var query = String.format(listTemplate, listId, url);
        var statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();

        return generatedKeys.getInt(1);
    }

    private void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);

        router.route().handler(routingContext -> {
            routingContext.setUser(User.create(new JsonObject().put("accountId", accountId1)));
            routingContext.next();
        });

        ErrorHandlers.configure(router);

        vertx.deployVerticle(new ListHttpVerticle(router));
        vertx.deployVerticle(new ListEntityVerticle(TEST_CONFIG.getJdbcOptions()));
        server.listen(port);
        vertxTestContext.completeNow();
    }

    @Test
    public void returnsAllFinalizedListsForQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_QUIZ_ID_1 + "/list"))
                .build();
        var listResponse = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).isEqualTo(new JsonArray());

        request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/finalize"))
                .build();
        var finalizeResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(finalizeResponse.statusCode()).isEqualTo(201);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_QUIZ_ID_1 + "/list"))
                .build();
        listResponse = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).isNotNull();
        assertThat(listResponse.body()).hasSize(1);
        var list = listResponse.body().getJsonObject(0);
        assertThat(list.getInteger("id")).isEqualTo(listId1);
        assertThat(list.getInteger("assigneeId")).isNull();

        request = HttpRequest.newBuilder()
                .PUT(BodyPublisher.ofJsonObject(new JsonObject().put("assigneeId", EXTERNAL_ACCOUNT_ID_2)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/assign"))
                .build();
        var assignResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(assignResponse.statusCode()).isEqualTo(201);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_QUIZ_ID_1 + "/list"))
                .build();
        listResponse = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).isNotNull();
        assertThat(listResponse.body()).hasSize(1);
        list = listResponse.body().getJsonObject(0);
        assertThat(list.getInteger("id")).isEqualTo(listId1);
        assertThat(list.getString("assigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsAllListsForAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list"))
                .build();
        var response = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(1);
        var list = response.body().getJsonObject(0);
        assertThat(list.getInteger("id")).isEqualTo(listId1);
        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL_1)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/video"))
                .build();
        var addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1))
                .build();
        var getListResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(getListResponse.statusCode()).isEqualTo(200);
        var body = getListResponse.body();
        assertThat(body.getInteger("id")).isEqualTo(listId1);
        assertThat(body.getBoolean("hasDraftStatus")).isTrue();
        var videos = body.getJsonArray("videos");
        assertThat(videos).hasSize(1);
        var video = videos.getJsonObject(0);
        assertThat(video.getInteger("id")).isNotNull();
        assertThat(video.getString("url")).isEqualTo(EMBEDDABLE_URL_1);

        vertxTestContext.completeNow();
    }

    @Test
    public void returns404GettingUnknownList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var nonExistingListId = listId1 - 1;
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + nonExistingListId))
                .build();
        var getListResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(getListResponse.statusCode()).isEqualTo(404);
        assertThat(getListResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsSingleListForSameQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId2))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        var body = response.body();
        assertThat(body.getInteger("id")).isEqualTo(listId2);
        assertThat(body.getBoolean("hasDraftStatus")).isTrue();
        assertThat(body.getJsonArray("videos")).isEmpty();
        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotReturnListForOtherQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId3))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(403);
        var body = response.body();
        assertThat(body.getString("error")).isEqualTo(String.format("Account \"%d\" cannot access list \"%d\"", accountId1, listId3));
        vertxTestContext.completeNow();
    }

    @Test
    public void addsVideoToOwnList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL_1)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/video"))
                .build();
        var addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        var body = addVideoResponse.body();
        assertThat(body.getInteger("id")).isNotNull();
        assertThat(body.getString("url")).isEqualTo(EMBEDDABLE_URL_1);

        request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL_2)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/video"))
                .build();
        addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        body = addVideoResponse.body();
        assertThat(body.getInteger("id")).isNotNull();
        assertThat(body.getString("url")).isEqualTo(EMBEDDABLE_URL_2);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1))
                .build();
        var listResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        var list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId1);
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
    public void doesNotAddVideoToListForOtherAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL_1)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId3 + "/video"))
                .build();
        var addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(403);
        assertThat(addVideoResponse.body().getString("error")).isEqualTo(String.format("Account \"%d\" did not create list \"%d\"", accountId1, listId3));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAddVideoToNonExistingList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var nonExistingListId = listId1 - 1;
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL_1)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + nonExistingListId + "/video"))
                .build();
        var addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(404);
        assertThat(addVideoResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void deletesVideoFromOwnList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL_1)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/video"))
                .build();
        var addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        var body = addVideoResponse.body();
        assertThat(body.getInteger("id")).isNotNull();
        assertThat(body.getString("url")).isEqualTo(EMBEDDABLE_URL_1);

        request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL_2)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/video"))
                .build();
        addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);
        body = addVideoResponse.body();
        assertThat(body.getInteger("id")).isNotNull();
        assertThat(body.getString("url")).isEqualTo(EMBEDDABLE_URL_2);

        var videoId = body.getInteger("id");

        request = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create("http://localhost:" + port + "/private/video/" + videoId))
                .build();
        var deleteResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(deleteResponse.statusCode()).isEqualTo(201);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotDeleteVideoFromListOfOtherAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create("http://localhost:" + port + "/private/video/" + videoId))
                .build();
        var deleteVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(deleteVideoResponse.statusCode()).isEqualTo(403);
        assertThat(deleteVideoResponse.body().getString("error")).isEqualTo(String.format("Account \"%d\" did not create list \"%d\"", accountId1, listId3));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotDeleteNonExistingVideo(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var nonExistingVideoId = 0;
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create("http://localhost:" + port + "/private/video/" + nonExistingVideoId))
                .build();
        var addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(404);
        assertThat(addVideoResponse.body().getString("error")).isEqualTo(String.format("List for video ID \"%d\" not found", nonExistingVideoId));

        vertxTestContext.completeNow();
    }

    @Test
    public void finalizesListForOwnAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1 + "/finalize"))
                .build();
        var finalizeResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(finalizeResponse.statusCode()).isEqualTo(201);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId1))
                .build();
        var listResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        var list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId1);
        assertThat(list.getJsonArray("videos")).isEmpty();
        assertThat(list.getBoolean("hasDraftStatus")).isFalse();

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotFinalizeListForOtherAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId2 + "/finalize"))
                .build();
        var finalizeResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(finalizeResponse.statusCode()).isEqualTo(403);
        assertThat(finalizeResponse.body().getString("error")).isEqualTo(String.format("Account \"%d\" did not create list \"%d\"", accountId1, listId2));

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotFinalizeListNonExistingList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var nonExistingListId = listId1 - 1;
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/list/" + nonExistingListId + "/finalize"))
                .build();
        var finalizeResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(finalizeResponse.statusCode()).isEqualTo(404);
        assertThat(finalizeResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }

    @Test
    public void assignsList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(BodyPublisher.ofJsonObject(new JsonObject().put("assigneeId", EXTERNAL_ACCOUNT_ID_2)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId2 + "/assign"))
                .build();
        var assignResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(assignResponse.statusCode()).isEqualTo(201);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId2))
                .build();
        var listResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        var list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId2);
        assertThat(list.getString("assigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_2);

        request = HttpRequest.newBuilder()
                .PUT(BodyPublisher.ofJsonObject(new JsonObject().put("assigneeId", EXTERNAL_ACCOUNT_ID_1)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId2 + "/assign"))
                .build();
        assignResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(assignResponse.statusCode()).isEqualTo(201);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId2))
                .build();
        listResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        list = listResponse.body();
        assertThat(list.getInteger("id")).isEqualTo(listId2);
        assertThat(list.getString("assigneeId")).isEqualTo(EXTERNAL_ACCOUNT_ID_1);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAssignListToAccountOutsideQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(BodyPublisher.ofJsonObject(new JsonObject().put("assigneeId", EXTERNAL_ACCOUNT_ID_3)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId2 + "/assign"))
                .build();
        var assignResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(assignResponse.statusCode()).isEqualTo(403);
        var expectedMessage = String.format("Account with external ID \"%s\" does not participate in quiz with ID \"%s\"", EXTERNAL_ACCOUNT_ID_3, quizId1);
        assertThat(assignResponse.body().getString("error")).isEqualTo(expectedMessage);

        vertxTestContext.completeNow();
    }

    @Test
    public void doesNotAssignToNonExistingList(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var nonExistingListId = listId1 - 1;
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .PUT(BodyPublisher.ofJsonObject(new JsonObject().put("assigneeId", accountId3)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + nonExistingListId + "/assign"))
                .build();
        var assignResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(assignResponse.statusCode()).isEqualTo(404);
        assertThat(assignResponse.body().getString("error")).isEqualTo(String.format("List \"%d\" not found", nonExistingListId));

        vertxTestContext.completeNow();
    }
}
