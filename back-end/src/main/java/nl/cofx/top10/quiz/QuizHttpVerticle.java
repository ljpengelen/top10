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
import nl.cofx.top10.random.TokenGenerator;

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

        router.route(HttpMethod.POST, "/private/quiz/:externalId/participate").handler(this::handleParticipate);

        router.route(HttpMethod.GET, "/public/quiz/:externalId").handler(this::handleGetOne);
        router.route(HttpMethod.GET, "/private/quiz/:externalId/participants").handler(this::handleGetParticipants);
        router.route(HttpMethod.GET, "/private/quiz/:externalId/result").handler(this::handleGetResult);

        router.route(HttpMethod.PUT, "/private/quiz/:externalId/complete").handler(this::handleComplete);
    }

    private void handleGetAll(RoutingContext routingContext) {
        log.debug("Get all quizzes");

        var accountId = routingContext.user().principal().getInteger("accountId");
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

        var accountId = routingContext.user().principal().getInteger("accountId");
        var externalId = TokenGenerator.generateToken();
        var createRequest = toCreateRequest(accountId, routingContext, externalId);
        vertx.eventBus().request(CREATE_QUIZ_ADDRESS, createRequest, createQuizReply -> {
            if (createQuizReply.failed()) {
                handleFailure(createQuizReply.cause(), routingContext);
                return;
            }

            log.debug("Created quiz with external ID \"{}\"", externalId);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("externalId", externalId).toBuffer());
        });
    }

    private JsonObject toCreateRequest(Integer accountId, RoutingContext routingContext, String externalId) {
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
                .put("externalId", externalId)
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
        var externalId = routingContext.pathParam("externalId");

        log.debug(String.format("Get quiz \"%s\"", externalId));

        Integer accountId = null;
        if (routingContext.user() != null) {
            accountId = routingContext.user().principal().getInteger("accountId");
        }
        var getQuizRequest = new JsonObject()
                .put("accountId", accountId)
                .put("externalId", externalId);
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
        var externalId = routingContext.pathParam("externalId");

        log.debug("Get participants for quiz \"{}\"", externalId);

        var accountId = routingContext.user().principal().getInteger("accountId");
        var getParticipantsRequest = new JsonObject()
                .put("accountId", accountId)
                .put("externalId", externalId);
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
        var externalId = routingContext.pathParam("externalId");

        log.debug(String.format("Get results for quiz \"%s\"", externalId));

        var accountId = routingContext.user().principal().getInteger("accountId");
        var getQuizResultRequest = new JsonObject()
                .put("accountId", accountId)
                .put("externalId", externalId);
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
        var externalId = routingContext.pathParam("externalId");

        log.debug(String.format("Complete quiz with external ID \"%s\"", externalId));

        var accountId = routingContext.user().principal().getInteger("accountId");
        var completeRequest = new JsonObject()
                .put("accountId", accountId)
                .put("externalId", externalId);
        vertx.eventBus().request(COMPLETE_QUIZ_ADDRESS, completeRequest, completeQuizReply -> {
            if (completeQuizReply.failed()) {
                handleFailure(completeQuizReply.cause(), routingContext);
                return;
            }

            log.debug("Completed quiz with external ID \"{}\"", externalId);

            routingContext.response()
                    .setStatusCode(204)
                    .end();
        });
    }

    private void handleParticipate(RoutingContext routingContext) {
        var externalId = routingContext.pathParam("externalId");

        log.debug("Participate in quiz with external ID \"{}\"", externalId);

        var accountId = routingContext.user().principal().getInteger("accountId");
        var participateRequest = new JsonObject()
                .put("accountId", accountId)
                .put("externalId", externalId);
        vertx.eventBus().request(PARTICIPATE_IN_QUIZ_ADDRESS, participateRequest, participateReply -> {
            if (participateReply.failed()) {
                handleFailure(participateReply.cause(), routingContext);
                return;
            }

            var listId = (Integer) participateReply.result().body();
            log.debug("Created list with ID \"{}\" for participating in quiz with external ID \"{}\"", listId, externalId);

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
