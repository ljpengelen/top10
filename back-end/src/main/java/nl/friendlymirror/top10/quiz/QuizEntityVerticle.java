package nl.friendlymirror.top10.quiz;

import java.time.Instant;
import java.util.stream.Collectors;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
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
    public static final String GET_PARTICIPANTS_ADDRESS = "entity.quiz.participants";

    private static final String GET_ALL_QUIZZES_TEMPLATE = "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline, q.external_id FROM quiz q "
                                                           + "NATURAL JOIN participant p "
                                                           + "WHERE p.account_id = ?";
    private static final String GET_ONE_QUIZ_TEMPLATE = "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline, q.external_id FROM quiz q "
                                                        + "WHERE q.external_id = ?";
    private static final String CREATE_QUIZ_TEMPLATE = "INSERT INTO quiz (name, is_active, creator_id, deadline, external_id) VALUES (?, true, ?, ?, ?)";
    private static final String COMPLETE_QUIZ_TEMPLATE = "UPDATE quiz SET is_active = false WHERE creator_id = ? AND external_id = ?";
    private static final String PARTICIPATE_IN_QUIZ_TEMPLATE = "INSERT INTO participant (account_id, quiz_id) VALUES (?, (SELECT quiz_id from quiz WHERE external_id = ?)) ON CONFLICT DO NOTHING";
    private static final String CREATE_LIST_TEMPLATE = "INSERT INTO list (account_id, quiz_id, has_draft_status) VALUES (?, (SELECT quiz_id from quiz WHERE external_id = ?), true) ON CONFLICT DO NOTHING";
    private static final String GET_PARTICIPANTS_TEMPLATE = "SELECT a.account_id, a.name FROM account a "
                                                            + "NATURAL JOIN participant p "
                                                            + "JOIN quiz q ON p.quiz_id = q.quiz_id "
                                                            + "WHERE q.external_id = ?";

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
        vertx.eventBus().consumer(GET_PARTICIPANTS_ADDRESS, this::handleGetAllParticipants);
    }

    private void handleGetAll(Message<Integer> getAllQuizzesRequest) {
        var accountId = getAllQuizzesRequest.body();
        sqlClient.queryWithParams(GET_ALL_QUIZZES_TEMPLATE, new JsonArray().add(accountId), asyncQuizzes -> {
            if (asyncQuizzes.failed()) {
                log.error("Unable to retrieve all quizzes for account ID \"{}\"", accountId, asyncQuizzes.cause());
                getAllQuizzesRequest.fail(500, "Unable to retrieve all quizzes");
                return;
            }

            log.debug("Retrieved all quizzes for account");

            var quizzes = asyncQuizzes.result().getResults().stream()
                    .map(this::quizArrayToJsonObject)
                    .collect(Collectors.toList());

            getAllQuizzesRequest.reply(new JsonArray(quizzes));
        });
    }

    private JsonObject quizArrayToJsonObject(JsonArray array) {
        return new JsonObject()
                .put("id", array.getInteger(0))
                .put("name", array.getString(1))
                .put("isActive", array.getBoolean(2))
                .put("creatorId", array.getInteger(3))
                .put("deadline", array.getInstant(4))
                .put("externalId", array.getString(5));
    }

    private void handleGetOne(Message<String> getOneQuizRequest) {
        var externalId = getOneQuizRequest.body();
        sqlClient.querySingleWithParams(GET_ONE_QUIZ_TEMPLATE, new JsonArray().add(externalId), asyncQuiz -> {
            if (asyncQuiz.failed()) {
                log.error("Unable to retrieve quiz with external ID \"{}\"", externalId, asyncQuiz.cause());
                getOneQuizRequest.fail(500, "Unable to retrieve quiz");
                return;
            }

            log.debug("Retrieved quiz by external ID");

            getOneQuizRequest.reply(quizArrayToJsonObject(asyncQuiz.result()));
        });
    }

    private void handleCreate(Message<JsonObject> createRequest) {
        var body = createRequest.body();
        var creatorId = body.getInteger("creatorId");
        var name = body.getString("name");
        var deadline = body.getInstant("deadline");
        var externalId = body.getString("externalId");

        withTransaction(connection ->
                createQuiz(connection, name, creatorId, deadline, externalId)
                        .compose(quizId ->
                                CompositeFuture.all(
                                        participateInQuiz(connection, creatorId, externalId),
                                        createList(connection, creatorId, externalId)))
        ).onSuccess(nothing -> {
            log.debug("Created quiz");
            createRequest.reply(null);
        }).onFailure(cause -> {
            var errorMessage = "Unable to create quiz";
            log.error(errorMessage, cause);
            createRequest.fail(500, errorMessage);
        });
    }

    private Future<Integer> createQuiz(SQLConnection connection, String name, Integer creatorId, Instant deadline, String externalId) {
        var promise = Promise.<Integer> promise();

        var params = new JsonArray().add(name).add(creatorId).add(deadline).add(externalId);
        connection.updateWithParams(CREATE_QUIZ_TEMPLATE, params, asyncResult -> {
            if (asyncResult.failed()) {
                var cause = asyncResult.cause();
                log.error("Unable to execute query \"{}\"", CREATE_QUIZ_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            var quizId = asyncResult.result().getKeys().getInteger(0);
            log.debug("Query \"{}\" produced result \"{}\"", CREATE_QUIZ_TEMPLATE, quizId);
            promise.complete(quizId);
        });

        return promise.future();
    }

    private void handleComplete(Message<JsonObject> completeRequest) {
        var body = completeRequest.body();
        var accountId = body.getInteger("accountId");
        var externalId = body.getString("externalId");

        sqlClient.updateWithParams(COMPLETE_QUIZ_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncComplete -> {
            if (asyncComplete.failed()) {
                log.error("Unable to let account \"{}\" complete quiz with external ID \"{}\"", accountId, externalId, asyncComplete.cause());
                completeRequest.fail(500, "Unable to let account complete quiz");
                return;
            }

            var numberOfAffectedRows = asyncComplete.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Completed quiz by external ID");
            } else {
                log.debug("Account is not authorized to complete quiz by external ID");
            }

            completeRequest.reply(numberOfAffectedRows > 0);
        });
    }

    private void handleParticipate(Message<JsonObject> participateRequest) {
        var body = participateRequest.body();
        var accountId = body.getInteger("accountId");
        var externalId = body.getString("externalId");

        withTransaction(connection ->
                CompositeFuture.all(
                        participateInQuiz(connection, accountId, externalId),
                        createList(connection, accountId, externalId)))
                .onSuccess(nothing -> {
                    log.debug("Let account participate in quiz");
                    participateRequest.reply(null);
                })
                .onFailure(throwable -> {
                    log.error("Unable to let account \"{}\" participate in quiz with external ID \"{}\"", accountId, externalId, throwable);
                    participateRequest.fail(500, "Unable to let account participate in quiz");
                });
    }

    private Future<Boolean> participateInQuiz(SQLConnection connection, Integer accountId, String externalId) {
        var promise = Promise.<Boolean> promise();

        connection.updateWithParams(PARTICIPATE_IN_QUIZ_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncUpdate -> {
            if (asyncUpdate.failed()) {
                var cause = asyncUpdate.cause();
                log.error("Unable to execute query \"{}\"", PARTICIPATE_IN_QUIZ_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            var numberOfAffectedRows = asyncUpdate.result().getUpdated();
            log.debug("Affected {} rows by executing query \"{}\"", numberOfAffectedRows, PARTICIPATE_IN_QUIZ_TEMPLATE);

            promise.complete(numberOfAffectedRows > 0);
        });

        return promise.future();
    }

    private Future<Boolean> createList(SQLConnection connection, Integer accountId, String externalId) {
        var promise = Promise.<Boolean> promise();

        connection.updateWithParams(CREATE_LIST_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncUpdate -> {
            if (asyncUpdate.failed()) {
                var cause = asyncUpdate.cause();
                log.error("Unable to execute query \"{}\"", CREATE_LIST_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            var numberOfAffectedRows = asyncUpdate.result().getUpdated();
            log.debug("Affected {} rows by executing query \"{}\"", numberOfAffectedRows, CREATE_LIST_TEMPLATE);

            promise.complete(numberOfAffectedRows > 0);
        });

        return promise.future();
    }

    private void handleGetAllParticipants(Message<String> getAllParticipantsRequest) {
        var externalId = getAllParticipantsRequest.body();
        sqlClient.queryWithParams(GET_PARTICIPANTS_TEMPLATE, new JsonArray().add(externalId), asyncParticipants -> {
            if (asyncParticipants.failed()) {
                log.error("Unable to retrieve all participants for external ID \"{}\"", externalId, asyncParticipants.cause());
                getAllParticipantsRequest.fail(500, "Unable to retrieve all participants");
                return;
            }

            log.debug("Retrieved all participants for quiz");

            var quizzes = asyncParticipants.result().getResults().stream()
                    .map(this::participantArrayToJsonObject)
                    .collect(Collectors.toList());

            getAllParticipantsRequest.reply(new JsonArray(quizzes));
        });
    }

    private JsonObject participantArrayToJsonObject(JsonArray array) {
        return new JsonObject()
                .put("id", array.getInteger(0))
                .put("name", array.getString(1));
    }
}
