package nl.friendlymirror.top10.quiz;

import static nl.friendlymirror.top10.quiz.QuizEntityVerticle.CREATE_QUIZ_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.*;
import java.time.Instant;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.friendlymirror.top10.config.TestConfig;
import nl.friendlymirror.top10.migration.MigrationVerticle;

@ExtendWith(VertxExtension.class)
class QuizEntityVerticleTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private int accountId;
    private EventBus eventBus;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.completing());
    }

    @BeforeEach
    public void cleanUp() throws SQLException {
        var connection = DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var statement = connection.prepareStatement("TRUNCATE TABLE quiz CASCADE");
        statement.execute();
    }

    @BeforeEach
    public void setUpAccount() throws SQLException {
        var connection = DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();

        statement = connection.prepareStatement("INSERT INTO account (name) VALUES ('John Doe')", Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        accountId = generatedKeys.getInt(1);
    }

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        eventBus = vertx.eventBus();
        vertx.deployVerticle(new QuizEntityVerticle(TEST_CONFIG.getJdbcOptions()), vertxTestContext.completing());
    }

    @Test
    public void createsQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("accountId", accountId)
                .put("name", "Greatest Hits")
                .put("deadline", Instant.now())
                .put("externalId", "abcdefg");
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncResult -> {
            vertxTestContext.verify(() -> {
                assertThat(asyncResult.succeeded()).isTrue();
            });
            vertxTestContext.completeNow();
        });
    }
}
