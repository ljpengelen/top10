package nl.cofx.top10.quiz;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.*;
import nl.cofx.top10.entity.AbstractEntityVerticle;

@Log4j2
@RequiredArgsConstructor
public class QuizEntityVerticle extends AbstractEntityVerticle {

    public static final String GET_ALL_QUIZZES_ADDRESS = "entity.quiz.getAll";
    public static final String GET_ONE_QUIZ_ADDRESS = "entity.quiz.getOne";
    public static final String GET_QUIZ_RESULT_ADDRESS = "entity.quiz.getResult";
    public static final String CREATE_QUIZ_ADDRESS = "entity.quiz.create";
    public static final String COMPLETE_QUIZ_ADDRESS = "entity.quiz.complete";
    public static final String PARTICIPATE_IN_QUIZ_ADDRESS = "entity.quiz.participate";
    public static final String GET_PARTICIPANTS_ADDRESS = "entity.quiz.participants";

    private final QuizRepository quizRepository = new QuizRepository();

    private final JsonObject jdbcOptions;

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        var eventBus = vertx.eventBus();
        eventBus.consumer(GET_ALL_QUIZZES_ADDRESS, this::handleGetAll);
        eventBus.consumer(GET_ONE_QUIZ_ADDRESS, this::handleGetOne);
        eventBus.consumer(GET_QUIZ_RESULT_ADDRESS, this::handleGetResult);
        eventBus.consumer(CREATE_QUIZ_ADDRESS, this::handleCreate);
        eventBus.consumer(COMPLETE_QUIZ_ADDRESS, this::handleComplete);
        eventBus.consumer(PARTICIPATE_IN_QUIZ_ADDRESS, this::handleParticipate);
        eventBus.consumer(GET_PARTICIPANTS_ADDRESS, this::handleGetAllParticipants);
    }

    private void handleGetAll(Message<Integer> getAllQuizzesRequest) {
        var accountId = getAllQuizzesRequest.body();
        withConnection(connection -> quizRepository.getAllQuizzes(connection, accountId))
                .onSuccess(getAllQuizzesRequest::reply)
                .onFailure(cause -> handleFailure(cause, getAllQuizzesRequest));
    }

    private void handleGetOne(Message<JsonObject> getOneQuizRequest) {
        var body = getOneQuizRequest.body();
        var externalId = body.getString("externalId");
        var accountId = body.getInteger("accountId");
        withConnection(connection -> quizRepository.getQuiz(connection, externalId, accountId))
                .onSuccess(getOneQuizRequest::reply)
                .onFailure(cause -> handleFailure(cause, getOneQuizRequest));
    }

    private void handleGetResult(Message<JsonObject> getQuizResultRequest) {
        var body = getQuizResultRequest.body();
        var externalId = body.getString("externalId");
        var accountId = body.getInteger("accountId");
        withTransaction(connection ->
                quizRepository.getQuiz(connection, externalId, accountId).compose(quiz ->
                        quizRepository.getQuizResult(connection, externalId)))
                .onSuccess(getQuizResultRequest::reply)
                .onFailure(cause -> handleFailure(cause, getQuizResultRequest));
    }

    private void handleCreate(Message<JsonObject> createRequest) {
        var body = createRequest.body();
        var creatorId = body.getInteger("creatorId");
        var name = body.getString("name");
        var deadline = body.getInstant("deadline");
        var externalId = body.getString("externalId");

        withTransaction(connection ->
                quizRepository.createQuiz(connection, name, creatorId, deadline, externalId).compose(quizId ->
                        quizRepository.createList(connection, creatorId, externalId))
        ).onSuccess(nothing -> {
            log.debug("Created quiz");
            createRequest.reply(null);
        }).onFailure(cause -> handleFailure(cause, createRequest));
    }

    private void handleComplete(Message<JsonObject> completeRequest) {
        var body = completeRequest.body();
        var accountId = body.getInteger("accountId");
        var externalId = body.getString("externalId");

        withTransaction(connection -> quizRepository.getQuiz(connection, externalId, accountId).compose(quiz -> {
            if (accountId.equals(quiz.getCreatorId())) {
                log.debug("Account \"{}\" is creator of quiz with external ID \"{}\"", accountId, externalId);
                return quizRepository.completeQuiz(connection, accountId, externalId);
            } else {
                log.debug("Account \"{}\" is not creator of quiz with external ID \"{}\"", accountId, externalId);
                return Future.failedFuture(new ForbiddenException(String.format("Account \"%d\" is not allowed to close quiz with external ID \"%s\"", accountId, externalId)));
            }
        })).onSuccess(nothing -> {
            log.debug("Successfully completed quiz");
            completeRequest.reply(null);
        }).onFailure(cause -> handleFailure(cause, completeRequest));
    }

    private void handleParticipate(Message<JsonObject> participateRequest) {
        var body = participateRequest.body();
        var accountId = body.getInteger("accountId");
        var externalId = body.getString("externalId");

        withTransaction(connection ->
                quizRepository.getQuiz(connection, externalId, accountId).compose(quiz ->
                        quizRepository.createList(connection, accountId, externalId)))
                .onSuccess(listId -> {
                    log.debug("Successfully let account participate in quiz");
                    participateRequest.reply(listId);
                })
                .onFailure(cause -> handleFailure(cause, participateRequest));
    }

    private void handleGetAllParticipants(Message<JsonObject> getAllParticipantsRequest) {
        var body = getAllParticipantsRequest.body();
        var externalId = body.getString("externalId");
        var accountId = body.getInteger("accountId");
        withTransaction(connection ->
                quizRepository.getQuiz(connection, externalId, accountId)
                        .compose(quiz -> quizRepository.getAllParticipants(connection, externalId)))
                .onSuccess(getAllParticipantsRequest::reply)
                .onFailure(cause -> handleFailure(cause, getAllParticipantsRequest));
    }

    private <T> void handleFailure(Throwable cause, Message<T> message) {
        var errorMessage = cause.getMessage();
        if (cause instanceof ForbiddenException) {
            message.fail(403, errorMessage);
        } else if (cause instanceof NotFoundException) {
            message.fail(404, errorMessage);
        } else if (cause instanceof ConflictException) {
            message.fail(409, errorMessage);
        } else {
            log.error("An unexpected error occurred: " + errorMessage);
            message.fail(500, errorMessage);
        }
    }
}
