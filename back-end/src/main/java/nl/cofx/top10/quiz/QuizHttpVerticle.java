package nl.cofx.top10.quiz;

import static nl.cofx.top10.quiz.QuizEntityVerticle.*;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.*;
import nl.cofx.top10.quiz.dto.*;

@Log4j2
@RequiredArgsConstructor
public class QuizHttpVerticle extends AbstractVerticle {

    private final Router router;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/private/quiz").handler(this::handleGetAll);

        router.route(HttpMethod.POST, "/private/quiz")
                .handler(BodyHandler.create())
                .handler(this::handleCreate);

        router.route(HttpMethod.POST, "/private/quiz/:quizId/participate").handler(this::handleParticipate);

        router.route(HttpMethod.GET, "/public/quiz/:quizId").handler(this::handleGetOne);
        router.route(HttpMethod.GET, "/private/quiz/:quizId/participants").handler(this::handleGetParticipants);
        router.route(HttpMethod.GET, "/private/quiz/:quizId/result").handler(this::handleGetResult);

        router.route(HttpMethod.PUT, "/private/quiz/:quizId/complete").handler(this::handleComplete);
    }

    private void handleGetAll(RoutingContext routingContext) {
        log.debug("Get all quizzes");

        var accountId = routingContext.user().principal().getString("accountId");
        vertx.eventBus().request(GET_ALL_QUIZZES_ADDRESS, accountId, allQuizzesReply -> {
            if (allQuizzesReply.failed()) {
                handleFailure(allQuizzesReply.cause(), routingContext);
                return;
            }

            var quizzesDto = (QuizzesDto) allQuizzesReply.result().body();
            log.debug("Retrieved {} quizzes", quizzesDto.getQuizzes().size());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(quizzesDto.toJsonArray().toBuffer());
        });
    }

    private void handleCreate(RoutingContext routingContext) {
        log.debug("Create quiz");

        var accountId = routingContext.user().principal().getString("accountId");
        var createRequest = toCreateRequest(accountId, routingContext);
        vertx.eventBus().request(CREATE_QUIZ_ADDRESS, createRequest, createQuizReply -> {
            if (createQuizReply.failed()) {
                handleFailure(createQuizReply.cause(), routingContext);
                return;
            }

            var quizId = (String) createQuizReply.result().body();
            log.debug("Created quiz \"{}\"", quizId);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("id", quizId).toBuffer());
        });
    }

    private JsonObject toCreateRequest(String accountId, RoutingContext routingContext) {
        var request = getRequestBodyAsJson(routingContext);
        if (request == null) {
            throw new ValidationException("Request body is empty");
        }

        var deadline = getInstant(request, "deadline");
        if (deadline == null) {
            throw new ValidationException("Deadline is missing");
        }

        var name = request.getString("name");
        if (StringUtils.isBlank(name)) {
            throw new ValidationException("Name is blank");
        }

        return new JsonObject()
                .put("creatorId", accountId)
                .put("deadline", deadline)
                .put("name", name);
    }

    private Instant getInstant(JsonObject request, String key) {
        try {
            return request.getInstant(key);
        } catch (Exception e) {
            log.debug("Unable to parse \"{}\" as instant", request.getString(key), e);
            throw new ValidationException(String.format("Invalid instant provided for property \"%s\"", key));
        }
    }

    private JsonObject getRequestBodyAsJson(RoutingContext routingContext) {
        try {
            return routingContext.getBodyAsJson();
        } catch (Exception e) {
            log.warn("Unable to parse request body as JSON", e);
            return null;
        }
    }

    private void handleGetOne(RoutingContext routingContext) {
        var quizId = routingContext.pathParam("quizId");

        log.debug(String.format("Get quiz \"%s\"", quizId));

        String accountId = null;
        if (routingContext.user() != null) {
            accountId = routingContext.user().principal().getString("accountId");
        }
        var getQuizRequest = new JsonObject()
                .put("accountId", accountId)
                .put("quizId", quizId);
        vertx.eventBus().request(GET_ONE_QUIZ_ADDRESS, getQuizRequest, quizReply -> {
            if (quizReply.failed()) {
                handleFailure(quizReply.cause(), routingContext);
                return;
            }

            var quizDto = (QuizDto) quizReply.result().body();
            log.debug("Retrieved quiz \"{}\"", quizDto);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(quizDto.toJsonObject().toBuffer());
        });
    }

    private void handleGetParticipants(RoutingContext routingContext) {
        var quizId = routingContext.pathParam("quizId");

        log.debug("Get participants for quiz \"{}\"", quizId);

        var accountId = routingContext.user().principal().getString("accountId");
        var getParticipantsRequest = new JsonObject()
                .put("accountId", accountId)
                .put("quizId", quizId);
        vertx.eventBus().request(GET_PARTICIPANTS_ADDRESS, getParticipantsRequest, participantsReply -> {
            if (participantsReply.failed()) {
                handleFailure(participantsReply.cause(), routingContext);
                return;
            }

            var participants = (JsonArray) participantsReply.result().body();
            log.debug("Retrieved {} participants", participants.size());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(participants.toBuffer());
        });
    }

    private void handleGetResult(RoutingContext routingContext) {
        var quizId = routingContext.pathParam("quizId");

        log.debug(String.format("Get results for quiz \"%s\"", quizId));

        var accountId = routingContext.user().principal().getString("accountId");
        var getQuizResultRequest = new JsonObject()
                .put("accountId", accountId)
                .put("quizId", quizId);
        vertx.eventBus().request(GET_QUIZ_RESULT_ADDRESS, getQuizResultRequest, quizResultReply -> {
            if (quizResultReply.failed()) {
                handleFailure(quizResultReply.cause(), routingContext);
                return;
            }

            var quizResult = (ResultSummaryDto) quizResultReply.result().body();
            log.debug("Retrieved result for quiz \"{}\"", quizResult);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(quizResult.toJsonObject().toBuffer());
        });
    }

    private void handleComplete(RoutingContext routingContext) {
        var quizId = routingContext.pathParam("quizId");

        log.debug(String.format("Complete quiz \"%s\"", quizId));

        var accountId = routingContext.user().principal().getString("accountId");
        var completeRequest = new JsonObject()
                .put("accountId", accountId)
                .put("quizId", quizId);
        vertx.eventBus().request(COMPLETE_QUIZ_ADDRESS, completeRequest, completeQuizReply -> {
            if (completeQuizReply.failed()) {
                handleFailure(completeQuizReply.cause(), routingContext);
                return;
            }

            log.debug("Completed quiz \"{}\"", quizId);

            routingContext.response()
                    .setStatusCode(204)
                    .end();
        });
    }

    private void handleParticipate(RoutingContext routingContext) {
        var quizId = routingContext.pathParam("quizId");

        log.debug("Participate in quiz \"{}\"", quizId);

        var accountId = routingContext.user().principal().getString("accountId");
        var participateRequest = new JsonObject()
                .put("accountId", accountId)
                .put("quizId", quizId);
        vertx.eventBus().request(PARTICIPATE_IN_QUIZ_ADDRESS, participateRequest, participateReply -> {
            if (participateReply.failed()) {
                handleFailure(participateReply.cause(), routingContext);
                return;
            }

            var listId = (String) participateReply.result().body();
            log.debug("Created list \"{}\" for participating in quiz \"{}\"", listId, quizId);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("personalListId", listId).toBuffer());
        });
    }

    private void handleFailure(Throwable cause, RoutingContext routingContext) {
        var replyException = (ReplyException) cause;
        if (replyException.failureCode() == 404) {
            routingContext.fail(new NotFoundException(replyException.getMessage()));
        } else if (replyException.failureCode() == 403) {
            routingContext.fail(new ForbiddenException(replyException.getMessage()));
        } else if (replyException.failureCode() == 409) {
            routingContext.fail(new ConflictException(cause.getMessage()));
        } else {
            routingContext.fail(new InternalServerErrorException(replyException.getMessage(), replyException));
        }
    }
}
