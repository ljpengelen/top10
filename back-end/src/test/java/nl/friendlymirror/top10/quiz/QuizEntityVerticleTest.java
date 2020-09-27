package nl.friendlymirror.top10.quiz;

import static nl.friendlymirror.top10.quiz.QuizEntityVerticle.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.*;
import java.time.Instant;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.friendlymirror.top10.config.TestConfig;
import nl.friendlymirror.top10.migration.MigrationVerticle;

@ExtendWith(VertxExtension.class)
class QuizEntityVerticleTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String QUIZ_NAME = "Greatest Hits";
    private static final Instant DEADLINE = Instant.now();
    private static final String EXTERNAL_ID = "abcdefg";
    private static final String NAME = "John Doe";
    private static final String ALTERNATIVE_NAME = "Jane Doe";

    private int accountId;
    private int alternativeAccountId;
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
    public void setUpAccounts() throws SQLException {
        var connection = DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();

        statement = connection.prepareStatement("INSERT INTO account (name) VALUES ('" + NAME + "')", Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        var generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        accountId = generatedKeys.getInt(1);

        statement = connection.prepareStatement("INSERT INTO account (name) VALUES ('" + ALTERNATIVE_NAME + "')", Statement.RETURN_GENERATED_KEYS);
        statement.execute();

        generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        alternativeAccountId = generatedKeys.getInt(1);
    }

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        eventBus = vertx.eventBus();
        vertx.deployVerticle(new QuizEntityVerticle(TEST_CONFIG.getJdbcOptions()), vertxTestContext.completing());
    }

    @Test
    public void createsQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncResult -> {
            vertxTestContext.verify(() -> assertThat(asyncResult.succeeded()).isTrue());
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void returnsSingleQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate ->
                eventBus.request(GET_ONE_QUIZ_ADDRESS, EXTERNAL_ID, asyncGetOne -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncGetOne.succeeded()).isTrue();

                        var quiz = (JsonObject) asyncGetOne.result().body();
                        assertThat(quiz.getInteger("id")).isNotNull();
                        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
                        assertThat(quiz.getBoolean("isActive")).isTrue();
                        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
                        assertThat(quiz.getString("externalId")).isEqualTo(EXTERNAL_ID);
                    });
                    vertxTestContext.completeNow();
                }));
    }

    @Test
    public void returnsParticipants(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate ->
                eventBus.request(GET_PARTICIPANTS_ADDRESS, EXTERNAL_ID, asyncParticipants -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncParticipants.succeeded()).isTrue();

                        var participants = (JsonArray) asyncParticipants.result().body();
                        assertThat(participants).hasSize(1);
                        var participant = participants.getJsonObject(0);
                        assertThat(participant.getInteger("id")).isEqualTo(accountId);
                        assertThat(participant.getString("name")).isEqualTo(NAME);
                    });
                    vertxTestContext.completeNow();
                }));
    }

    @Test
    public void returnsQuizzesForAccount(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate ->
                eventBus.request(GET_ALL_QUIZZES_ADDRESS, accountId, asyncGetAll -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncGetAll.succeeded()).isTrue();

                        var quizzes = (JsonArray) asyncGetAll.result().body();
                        assertThat(quizzes).hasSize(1);

                        var quiz = quizzes.getJsonObject(0);
                        assertThat(quiz.getInteger("id")).isNotNull();
                        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
                        assertThat(quiz.getBoolean("isActive")).isTrue();
                        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
                        assertThat(quiz.getString("externalId")).isEqualTo(EXTERNAL_ID);
                    });
                    vertxTestContext.completeNow();
                }));
    }

    @Test
    public void completesQuizAsCreator(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate -> {
            var closeRequest = new JsonObject()
                    .put("accountId", accountId)
                    .put("externalId", EXTERNAL_ID);
            eventBus.request(COMPLETE_QUIZ_ADDRESS, closeRequest, asyncComplete -> {
                vertxTestContext.verify(() -> assertThat((Boolean) asyncComplete.result().body()).isTrue());
                eventBus.request(GET_ONE_QUIZ_ADDRESS, EXTERNAL_ID, asyncGetOne -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncGetOne.succeeded()).isTrue();

                        var quiz = (JsonObject) asyncGetOne.result().body();
                        assertThat(quiz.getInteger("id")).isNotNull();
                        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
                        assertThat(quiz.getBoolean("isActive")).isFalse();
                        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
                        assertThat(quiz.getString("externalId")).isEqualTo(EXTERNAL_ID);
                    });
                    vertxTestContext.completeNow();
                });
            });
        });
    }

    @Test
    public void doesNotCompleteQuizAsNonCreator(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate -> {
            var closeRequest = new JsonObject()
                    .put("accountId", alternativeAccountId)
                    .put("externalId", EXTERNAL_ID);
            eventBus.request(COMPLETE_QUIZ_ADDRESS, closeRequest, asyncComplete -> {
                vertxTestContext.verify(() -> assertThat((Boolean) asyncComplete.result().body()).isFalse());

                eventBus.request(GET_ONE_QUIZ_ADDRESS, EXTERNAL_ID, asyncGetOne -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncGetOne.succeeded()).isTrue();

                        var quiz = (JsonObject) asyncGetOne.result().body();
                        assertThat(quiz.getInteger("id")).isNotNull();
                        assertThat(quiz.getString("name")).isEqualTo(QUIZ_NAME);
                        assertThat(quiz.getBoolean("isActive")).isTrue();
                        assertThat(quiz.getInstant("deadline")).isEqualTo(DEADLINE);
                        assertThat(quiz.getString("externalId")).isEqualTo(EXTERNAL_ID);
                    });
                    vertxTestContext.completeNow();
                });
            });
        });
    }

    @Test
    public void letsAccountsParticipateInQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate -> {
            var participateRequest = new JsonObject()
                    .put("accountId", alternativeAccountId)
                    .put("externalId", EXTERNAL_ID);
            eventBus.request(PARTICIPATE_IN_QUIZ_ADDRESS, participateRequest, asyncParticipate ->
                    eventBus.request(GET_ALL_QUIZZES_ADDRESS, alternativeAccountId, asyncGetAll -> {
                        vertxTestContext.verify(() -> {
                            assertThat(asyncGetAll.succeeded()).isTrue();
                            assertThat((JsonArray) asyncGetAll.result().body()).hasSize(1);
                        });
                        vertxTestContext.completeNow();
                    }));
        });
    }
}
