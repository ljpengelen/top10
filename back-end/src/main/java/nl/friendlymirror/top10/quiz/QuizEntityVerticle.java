package nl.friendlymirror.top10.quiz;

import java.time.Instant;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.*;
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

    private static final String GET_ALL_QUIZZES_TEMPLATE =
            "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline, q.external_id, l.list_id, l.has_draft_status FROM quiz q "
            + "JOIN list l ON l.quiz_id = q.quiz_id "
            + "WHERE l.account_id = ?";
    private static final String GET_ONE_QUIZ_TEMPLATE = "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline, q.external_id, l.list_id, l.has_draft_status FROM quiz q "
                                                        + "LEFT JOIN list l ON l.quiz_id = q.quiz_id AND l.account_id = ? "
                                                        + "WHERE q.external_id = ?";
    private static final String CREATE_QUIZ_TEMPLATE = "INSERT INTO quiz (name, is_active, creator_id, deadline, external_id) VALUES (?, true, ?, ?, ?)";
    private static final String COMPLETE_QUIZ_TEMPLATE = "UPDATE quiz SET is_active = false WHERE creator_id = ? AND external_id = ?";
    private static final String CREATE_LIST_TEMPLATE = "INSERT INTO list (account_id, quiz_id, has_draft_status) "
                                                       + "VALUES (?, (SELECT quiz_id from quiz WHERE external_id = ?), true) "
                                                       + "ON CONFLICT DO NOTHING";
    private static final String GET_PARTICIPANTS_TEMPLATE = "SELECT a.external_id, a.name FROM account a "
                                                            + "JOIN list l ON l.account_id = a.account_id "
                                                            + "JOIN quiz q ON l.quiz_id = q.quiz_id "
                                                            + "WHERE q.external_id = ?";

    private final JsonObject jdbcOptions;

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        var eventBus = vertx.eventBus();
        eventBus.consumer(GET_ALL_QUIZZES_ADDRESS, this::handleGetAll);
        eventBus.consumer(GET_ONE_QUIZ_ADDRESS, this::handleGetOne);
        eventBus.consumer(CREATE_QUIZ_ADDRESS, this::handleCreate);
        eventBus.consumer(COMPLETE_QUIZ_ADDRESS, this::handleComplete);
        eventBus.consumer(PARTICIPATE_IN_QUIZ_ADDRESS, this::handleParticipate);
        eventBus.consumer(GET_PARTICIPANTS_ADDRESS, this::handleGetAllParticipants);
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
                    .map(quiz -> quizArrayToJsonObject(quiz, accountId))
                    .collect(Collectors.toList());

            getAllQuizzesRequest.reply(new JsonArray(quizzes));
        });
    }

    private JsonObject quizArrayToJsonObject(JsonArray array, Integer accountId) {
        var creatorId = array.getInteger(3);
        var quiz = new JsonObject()
                .put("id", array.getInteger(0))
                .put("name", array.getString(1))
                .put("isActive", array.getBoolean(2))
                .put("creatorId", creatorId)
                .put("isCreator", creatorId.equals(accountId))
                .put("deadline", array.getInstant(4))
                .put("externalId", array.getString(5));

        var personalListId = array.getInteger(6);
        if (personalListId != null) {
            quiz.put("personalListId", personalListId);
        }

        var personalListHasDraftStatus = array.getBoolean(7);
        if (personalListHasDraftStatus != null) {
            quiz.put("personalListHasDraftStatus", personalListHasDraftStatus);
        }

        return quiz;
    }

    private void handleGetOne(Message<JsonObject> getOneQuizRequest) {
        var body = getOneQuizRequest.body();
        var externalId = body.getString("externalId");
        var accountId = body.getInteger("accountId");
        withConnection(connection -> getQuiz(connection, externalId, accountId))
                .onSuccess(getOneQuizRequest::reply)
                .onFailure(cause -> {
                    if (cause instanceof NotFoundException) {
                        getOneQuizRequest.fail(404, cause.getMessage());
                    } else {
                        getOneQuizRequest.fail(500, String.format("Unable to retrieve quiz with external ID \"%s\"", externalId));
                    }
                });
    }

    private Future<JsonObject> getQuiz(SQLConnection connection, String externalId, Integer accountId) {
        var promise = Promise.<JsonObject> promise();

        connection.querySingleWithParams(GET_ONE_QUIZ_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncQuiz -> {
            if (asyncQuiz.failed()) {
                var cause = asyncQuiz.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_ONE_QUIZ_TEMPLATE, externalId, cause);
                promise.fail(cause);
                return;
            }

            if (asyncQuiz.result() == null) {
                log.debug("Quiz with external ID \"{}\" not found", externalId);
                promise.fail(new NotFoundException(String.format("Quiz with external ID \"%s\" not found", externalId)));
            } else {
                var quiz = quizArrayToJsonObject(asyncQuiz.result(), accountId);
                log.debug("Retrieved quiz by external ID \"{}\": \"{}\"", externalId, quiz);
                promise.complete(quiz);
            }
        });

        return promise.future();
    }

    private void handleCreate(Message<JsonObject> createRequest) {
        var body = createRequest.body();
        var creatorId = body.getInteger("creatorId");
        var name = body.getString("name");
        var deadline = body.getInstant("deadline");
        var externalId = body.getString("externalId");

        withTransaction(connection ->
                createQuiz(connection, name, creatorId, deadline, externalId).compose(quizId ->
                        createList(connection, creatorId, externalId))
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

        withTransaction(connection -> getQuiz(connection, externalId, accountId).compose(quiz -> {
            if (accountId.equals(quiz.getInteger("creatorId"))) {
                log.debug("Account \"{}\" is creator of quiz with external ID \"{}\"", accountId, externalId);
                return completeQuiz(connection, accountId, externalId);
            } else {
                log.debug("Account \"{}\" is not creator of quiz with external ID \"{}\"", accountId, externalId);
                return Future.failedFuture(new ForbiddenException(String.format("Account \"%d\" is not allowed to close quiz with external ID \"%s\"", accountId, externalId)));
            }
        })).onSuccess(nothing -> {
            log.debug("Successfully completed quiz");
            completeRequest.reply(null);
        }).onFailure(cause -> {
            if (cause instanceof NotFoundException) {
                completeRequest.fail(404, cause.getMessage());
            } else if (cause instanceof ForbiddenException) {
                completeRequest.fail(403, cause.getMessage());
            } else {
                log.error("Unable to let account \"{}\" complete quiz with external ID \"{}\"", accountId, externalId, cause);
                completeRequest.fail(500, "Unable to let account participate in quiz");
            }
        });
    }

    private void handleParticipate(Message<JsonObject> participateRequest) {
        var body = participateRequest.body();
        var accountId = body.getInteger("accountId");
        var externalId = body.getString("externalId");

        withTransaction(connection ->
                getQuiz(connection, externalId, accountId).compose(quiz ->
                        createList(connection, accountId, externalId)))
                .onSuccess(listId -> {
                    log.debug("Successfully let account participate in quiz");
                    participateRequest.reply(listId);
                })
                .onFailure(cause -> {
                    if (cause instanceof NotFoundException) {
                        participateRequest.fail(404, cause.getMessage());
                    } else if (cause instanceof ConflictException) {
                        participateRequest.fail(409, cause.getMessage());
                    } else {
                        log.error("Unable to let account \"{}\" participate in quiz with external ID \"{}\"", accountId, externalId, cause);
                        participateRequest.fail(500, "Unable to let account participate in quiz");
                    }
                });
    }

    private Future<Void> completeQuiz(SQLConnection connection, Integer accountId, String externalId) {
        var promise = Promise.<Void> promise();

        connection.updateWithParams(COMPLETE_QUIZ_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncComplete -> {
            if (asyncComplete.failed()) {
                var cause = asyncComplete.cause();
                log.error("Unable to execute query \"{}\" with parameters \"{}\" and \"{}\"", COMPLETE_QUIZ_TEMPLATE, accountId, externalId, cause);
                promise.fail(cause);
                return;
            }

            var numberOfAffectedRows = asyncComplete.result().getUpdated();
            log.debug("Affected {} rows by executing query \"{}\"", numberOfAffectedRows, COMPLETE_QUIZ_TEMPLATE);

            promise.complete();
        });

        return promise.future();
    }

    private Future<Integer> createList(SQLConnection connection, Integer accountId, String externalId) {
        var promise = Promise.<Integer> promise();

        connection.updateWithParams(CREATE_LIST_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncUpdate -> {
            if (asyncUpdate.failed()) {
                var cause = asyncUpdate.cause();
                log.error("Unable to execute query \"{}\"", CREATE_LIST_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            if (asyncUpdate.result().getUpdated() == 0) {
                var errorMessage = String.format("Account with ID \"%d\" already has a list for quiz with external ID \"%s\"", accountId, externalId);
                promise.fail(new ConflictException(errorMessage));
                return;
            }

            var listId = asyncUpdate.result().getKeys().getInteger(0);

            log.debug("Created new list with ID \"{}\"", listId);

            promise.complete(listId);
        });

        return promise.future();
    }

    private void handleGetAllParticipants(Message<JsonObject> getAllParticipantsRequest) {
        var body = getAllParticipantsRequest.body();
        var externalId = body.getString("externalId");
        var accountId = body.getInteger("accountId");
        withTransaction(connection ->
                getQuiz(connection, externalId, accountId)
                        .compose(quiz -> getAllParticipants(connection, externalId)))
                .onSuccess(getAllParticipantsRequest::reply)
                .onFailure(cause -> {
                    if (cause instanceof NotFoundException) {
                        getAllParticipantsRequest.fail(404, cause.getMessage());
                    } else {
                        log.error("Unable to get participants of quiz with external ID \"{}\"", externalId, cause);
                        getAllParticipantsRequest.fail(500, "Unable to get participants of quiz");
                    }
                });
    }

    private Future<JsonArray> getAllParticipants(SQLConnection connection, String externalId) {
        var promise = Promise.<JsonArray> promise();

        connection.queryWithParams(GET_PARTICIPANTS_TEMPLATE, new JsonArray().add(externalId), asyncParticipants -> {
            if (asyncParticipants.failed()) {
                var cause = asyncParticipants.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_PARTICIPANTS_TEMPLATE, externalId, cause);
                promise.fail(cause);
                return;
            }

            log.debug("Retrieved all participants for quiz");

            var quizzes = asyncParticipants.result().getResults().stream()
                    .map(this::participantArrayToJsonObject)
                    .collect(Collectors.toList());

            promise.complete(new JsonArray(quizzes));
        });

        return promise.future();
    }

    private JsonObject participantArrayToJsonObject(JsonArray array) {
        return new JsonObject()
                .put("id", array.getString(0))
                .put("name", array.getString(1));
    }
}
