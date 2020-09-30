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
import nl.friendlymirror.top10.http.*;
import nl.friendlymirror.top10.migration.MigrationVerticle;

@Log4j2
@ExtendWith(VertxExtension.class)
class ListVerticlesIntegrationTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String USERNAME = "John Doe";
    private static final String EMAIL_ADDRESS = "john.doe@example.com";
    private static final String ALTERNATIVE_USERNAME = "Jane Doe";
    private static final String ALTERNATIVE_EMAIL_ADDRESS = "jane.doe@example.org";

    private static final String QUIZ_NAME = "Greatest Hits";
    private static final Instant DEADLINE = Instant.now();
    private static final String EXTERNAL_ID = "abcdefg";
    private static final String URL = "https://www.youtube.com/watch?v=RBgcN9lrZ3g&list=PLsn6N7S-aJO3KeJnHmiT3rUcmZqesaj_b&index=9";

    private final int port = RandomPort.get();

    private int accountId;
    private int alternativeAccountId;
    private int quizId;
    private int listId;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.completing());
    }

    @BeforeEach
    public void setUpAccounts() throws SQLException {
        var connection = getConnection();
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();

        var accountQueryTemplate = "INSERT INTO account (name, email_address, first_login_at, last_login_at) VALUES ('%s', '%s', NOW(), NOW())";
        var query = String.format(accountQueryTemplate, USERNAME, EMAIL_ADDRESS);
        statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        accountId = generatedKeys.getInt(1);

        query = String.format(accountQueryTemplate, ALTERNATIVE_USERNAME, ALTERNATIVE_EMAIL_ADDRESS);
        statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        alternativeAccountId = generatedKeys.getInt(1);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
    }

    @BeforeEach
    public void setUpQuiz() throws SQLException {
        var connection = getConnection();
        connection.prepareStatement("TRUNCATE TABLE quiz CASCADE").execute();

        var query = String.format("INSERT INTO quiz (name, is_active, creator_id, deadline, external_id) VALUES ('%s', true, %d, '%s', '%s')",
                QUIZ_NAME, accountId, DEADLINE, EXTERNAL_ID);
        var statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        quizId = generatedKeys.getInt(1);

        query = String.format("INSERT INTO participant (account_id, quiz_id) VALUES (%d, %d)", accountId, quizId);
        connection.prepareStatement(query).execute();

        query = String.format("INSERT INTO list (account_id, quiz_id, has_draft_status) VALUES (%d, %d, true)", accountId, quizId);
        statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        listId = generatedKeys.getInt(1);
    }

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);

        router.route().handler(routingContext -> {
            routingContext.setUser(User.create(new JsonObject().put("accountId", accountId)));
            routingContext.next();
        });

        ErrorHandlers.configure(router);

        vertx.deployVerticle(new ListHttpVerticle(router));
        vertx.deployVerticle(new ListEntityVerticle(TEST_CONFIG.getJdbcOptions()));
        server.listen(port);
        vertxTestContext.completeNow();
    }

    @Test
    public void returnsAllListsForQuiz(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/list"))
                .build();
        var listResponse = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).isEqualTo(new JsonArray());

        request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", URL)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId + "/video"))
                .build();
        var addVideoResponse = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(addVideoResponse.statusCode()).isEqualTo(200);

        request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId + "/finalize"))
                .build();
        var finalizeResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(finalizeResponse.statusCode()).isEqualTo(201);

        request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/list"))
                .build();
        listResponse = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).isNotNull();
        assertThat(listResponse.body()).hasSize(1);
        var video = listResponse.body().getJsonObject(0);
        assertThat(video.getInteger("videoId")).isNotNull();
        assertThat(video.getInteger("listId")).isEqualTo(listId);
        assertThat(video.getString("url")).isEqualTo(URL);
        assertThat(video.getBoolean("hasDraftStatus")).isFalse();

        vertxTestContext.completeNow();
    }

    @Test
    public void returnsAllListsForAccount(VertxTestContext vertxTestContext) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + EXTERNAL_ID + "/list"))
                .build();
        var response = httpClient.send(request, new JsonArrayBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(new JsonArray());
        vertxTestContext.completeNow();
    }
}
