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
import io.vertx.core.eventbus.ReplyException;
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
    private static final String NON_EXISTING_EXTERNAL_ID = "pqrstuvw";
    private static final String USERNAME = "John Doe";
    private static final String ALTERNATIVE_USERNAME = "Jane Doe";
    private static final String EMAIL_ADDRESS = "john.doe@example.com";
    private static final String ALTERNATIVE_EMAIL_ADDRESS = "jane.doe@example.org";

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
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) throws SQLException {
        cleanUp();
        setUpAccounts();
        deployVerticle(vertx, vertxTestContext);
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

    private void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
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
            vertxTestContext.verify(() -> {
                assertThat(asyncResult.succeeded()).isTrue();

                var connection = getConnection();
                var statement = connection.prepareStatement("SELECT quiz_id, name, is_active, creator_id, deadline, external_id FROM quiz WHERE external_id = ?");
                statement.setString(1, EXTERNAL_ID);
                statement.execute();
                var resultSet = statement.getResultSet();

                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(2)).isEqualTo(QUIZ_NAME);
                assertThat(resultSet.getBoolean(3)).isTrue();
                assertThat(resultSet.getInt(4)).isEqualTo(accountId);
                assertThat(resultSet.getTimestamp(5)).isEqualTo(Timestamp.from(DEADLINE));
                assertThat(resultSet.getString(6)).isEqualTo(EXTERNAL_ID);

                var quizId = resultSet.getInt(1);

                statement = connection.prepareStatement("SELECT quiz_id, account_id FROM participant WHERE quiz_id = ? AND account_id = ?");
                statement.setInt(1, quizId);
                statement.setInt(2, accountId);
                statement.execute();
                resultSet = statement.getResultSet();

                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(quizId);
                assertThat(resultSet.getInt(2)).isEqualTo(accountId);

                statement = connection.prepareStatement("SELECT account_id, quiz_id, has_draft_status FROM list WHERE quiz_id = ? AND account_id = ?");
                statement.setInt(1, quizId);
                statement.setInt(2, accountId);
                statement.execute();
                resultSet = statement.getResultSet();

                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(accountId);
                assertThat(resultSet.getInt(2)).isEqualTo(quizId);
                assertThat(resultSet.getBoolean(3)).isTrue();
            });
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
    public void failsGetRequestWith404GivenUnknownQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate ->
                eventBus.request(GET_ONE_QUIZ_ADDRESS, NON_EXISTING_EXTERNAL_ID, asyncGetOne -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncGetOne.failed()).isTrue();
                        var cause = (ReplyException) asyncGetOne.cause();
                        assertThat(cause.failureCode()).isEqualTo(404);
                        assertThat(cause.getMessage()).isEqualTo(String.format("Quiz with external ID \"%s\" not found", NON_EXISTING_EXTERNAL_ID));
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
                        assertThat(participant.getString("name")).isEqualTo(USERNAME);
                    });
                    vertxTestContext.completeNow();
                }));
    }

    @Test
    public void failsParticipantsRequestWith404GivenUnknownQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate ->
                eventBus.request(GET_PARTICIPANTS_ADDRESS, NON_EXISTING_EXTERNAL_ID, asyncParticipants -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncParticipants.failed()).isTrue();

                        var cause = (ReplyException) asyncParticipants.cause();
                        assertThat(cause.failureCode()).isEqualTo(404);
                        assertThat(cause.getMessage()).isEqualTo(String.format("Quiz with external ID \"%s\" not found", NON_EXISTING_EXTERNAL_ID));
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
            var completionRequest = new JsonObject()
                    .put("accountId", accountId)
                    .put("externalId", EXTERNAL_ID);
            eventBus.request(COMPLETE_QUIZ_ADDRESS, completionRequest, asyncComplete -> {
                vertxTestContext.verify(() -> assertThat(asyncComplete.succeeded()).isTrue());
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
    public void failsCompletionRequestWith403GivenNonCreator(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate -> {
            var completionRequest = new JsonObject()
                    .put("accountId", alternativeAccountId)
                    .put("externalId", EXTERNAL_ID);
            eventBus.request(COMPLETE_QUIZ_ADDRESS, completionRequest, asyncComplete -> {
                vertxTestContext.verify(() -> {
                    assertThat(asyncComplete.failed()).isTrue();
                    var cause = (ReplyException) asyncComplete.cause();
                    assertThat(cause.failureCode()).isEqualTo(403);
                    var expectedMessage = String.format("Account \"%d\" is not allowed to close quiz with external ID \"%s\"", alternativeAccountId, EXTERNAL_ID);
                    assertThat(cause.getMessage()).isEqualTo(expectedMessage);
                });

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
    public void failsCompletionRequestWith404GivenUnknownQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate -> {
            var completionRequest = new JsonObject()
                    .put("accountId", alternativeAccountId)
                    .put("externalId", NON_EXISTING_EXTERNAL_ID);
            eventBus.request(COMPLETE_QUIZ_ADDRESS, completionRequest, asyncComplete -> {
                vertxTestContext.verify(() -> {
                    assertThat(asyncComplete.failed()).isTrue();
                    var cause = (ReplyException) asyncComplete.cause();
                    assertThat(cause.failureCode()).isEqualTo(404);
                    assertThat(cause.getMessage()).isEqualTo("Quiz with external ID \"pqrstuvw\" not found");
                });

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

    @Test
    public void failsParticipationRequestWith404GivenUnknownQuiz(VertxTestContext vertxTestContext) {
        var createRequest = new JsonObject()
                .put("creatorId", accountId)
                .put("name", QUIZ_NAME)
                .put("deadline", DEADLINE)
                .put("externalId", EXTERNAL_ID);
        eventBus.request(CREATE_QUIZ_ADDRESS, createRequest, asyncCreate -> {
            var participateRequest = new JsonObject()
                    .put("accountId", alternativeAccountId)
                    .put("externalId", NON_EXISTING_EXTERNAL_ID);
            eventBus.request(PARTICIPATE_IN_QUIZ_ADDRESS, participateRequest, asyncParticipate -> {
                vertxTestContext.verify(() -> {
                    assertThat(asyncParticipate.failed()).isTrue();
                    var cause = (ReplyException) asyncParticipate.cause();
                    assertThat(cause.failureCode()).isEqualTo(404);
                    assertThat(cause.getMessage()).isEqualTo(String.format("Quiz with external ID \"%s\" not found", NON_EXISTING_EXTERNAL_ID));
                });
                eventBus.request(GET_ALL_QUIZZES_ADDRESS, alternativeAccountId, asyncGetAll -> {
                    vertxTestContext.verify(() -> {
                        assertThat(asyncGetAll.succeeded()).isTrue();
                        assertThat((JsonArray) asyncGetAll.result().body()).isEmpty();
                    });
                    vertxTestContext.completeNow();
                });
            });
        });
    }
}
