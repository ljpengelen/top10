package nl.friendlymirror.top10.quiz;

import java.util.stream.Collectors;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.entity.AbstractEntityVerticle;

@Log4j2
@RequiredArgsConstructor
public class QuizEntityVerticle extends AbstractEntityVerticle {

    public static final String GET_ALL_QUIZZES_ADDRESS = "entity.quiz.getAll";
    public static final String GET_ONE_QUIZ_ADDRESS = "entity.quiz.getOne";
    public static final String CREATE_QUIZ_ADDRESS = "entity.quiz.create";
    public static final String COMPLETE_QUIZ_ADDRESS = "entity.quiz.complete";
    public static final String PARTICIPATE_IN_QUIZ_ADDRESS = "entity.quiz.participate";

    private static final String GET_ALL_QUIZZES_TEMPLATE = "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline FROM quiz q "
                                                           + "JOIN participant p ON q.creator_id = p.account_id "
                                                           + "WHERE p.account_id = ?";
    private static final String GET_ONE_QUIZ_TEMPLATE = "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline FROM quiz q "
                                                        + "WHERE q.quiz_id = ?";
    private static final String CREATE_QUIZ_TEMPLATE = "INSERT INTO quiz (name, is_active, creator_id, deadline) VALUES (?, true, ?, ?)";
    private static final String COMPLETE_QUIZ_TEMPLATE = "UPDATE quiz SET active = false WHERE creator_id = ? AND quiz_id = ?";
    private static final String PARTICIPATE_IN_QUIZ_TEMPLATE = "INSERT INTO participant (quiz_id, account_id) VALUES (?, ?) ON CONFLICT DO NOTHING";

    private final JsonObject jdbcOptions;

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        vertx.eventBus().consumer(GET_ALL_QUIZZES_ADDRESS, this::handleGetAll);
        vertx.eventBus().consumer(GET_ONE_QUIZ_ADDRESS, this::handleGetOne);
        vertx.eventBus().consumer(CREATE_QUIZ_ADDRESS, this::handleCreate);
        vertx.eventBus().consumer(COMPLETE_QUIZ_ADDRESS, this::handleComplete);
        vertx.eventBus().consumer(PARTICIPATE_IN_QUIZ_ADDRESS, this::handleParticipate);
    }

    private void handleGetAll(Message<Integer> accountIdMessage) {
        var accountId = accountIdMessage.body();
        sqlClient.queryWithParams(GET_ALL_QUIZZES_TEMPLATE, new JsonArray().add(accountId), asyncQuizzes -> {
            if (asyncQuizzes.failed()) {
                log.error("Unable to retrieve all quizzes for account ID \"{}\"", accountId, asyncQuizzes.cause());
                accountIdMessage.fail(500, "Unable to retrieve all quizzes");
                return;
            }

            var quizzes = asyncQuizzes.result().getResults().stream()
                    .map(array ->
                            new JsonObject()
                                    .put("id", array.getInteger(0))
                                    .put("name", array.getString(1))
                                    .put("isActive", array.getBoolean(2))
                                    .put("creatorId", array.getInteger(3))
                                    .put("deadline", array.getInstant(4)))
                    .collect(Collectors.toList());

            accountIdMessage.reply(new JsonArray(quizzes));
        });
    }

    private void handleGetOne(Message<Integer> quizId) {
    }

    private void handleCreate(Message<JsonObject> createRequest) {
    }

    private void handleComplete(Message<JsonObject> completeRequest) {
    }

    private void handleParticipate(Message<JsonObject> participateRequest) {
    }
}
