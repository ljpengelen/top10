package nl.friendlymirror.top10.quiz;

import static nl.friendlymirror.top10.quiz.QuizEntityVerticle.*;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.InternalServerErrorException;
import nl.friendlymirror.top10.ValidationException;

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

        router.route(HttpMethod.GET, "/private/quiz/:quizId").handler(this::handleGetOne);

        router.route(HttpMethod.POST, "/private/quiz/:quizId/complete").handler(this::handleComplete);

        router.route(HttpMethod.POST, "/private/quiz/:quizId/participate").handler(this::handleParticipate);
    }

    private void handleGetAll(RoutingContext routingContext) {
        log.debug("Get all quizzes");

        var accountId = routingContext.user().principal().getInteger("accountId");
        vertx.eventBus().request(GET_ALL_QUIZZES_ADDRESS, accountId, allQuizzesReply -> {
            if (allQuizzesReply.failed()) {
                routingContext.fail(new InternalServerErrorException(String.format("Unable to get all quizzes for account \"%s\"", accountId), allQuizzesReply.cause()));
                return;
            }

            var quizzes = (JsonArray) allQuizzesReply.result().body();
            log.debug("Retrieved {} quizzes", quizzes.size());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(quizzes.toBuffer());
        });
    }

    private void handleCreate(RoutingContext routingContext) {
        log.debug("Create quiz");

        var accountId = routingContext.user().principal().getInteger("accountId");
        var createRequest = toCreateRequest(accountId, routingContext);
        vertx.eventBus().request(CREATE_QUIZ_ADDRESS, createRequest, createQuizReply -> {
            if (createQuizReply.failed()) {
                throw new InternalServerErrorException(String.format("Unable to create quiz \"%s\"", createRequest), createQuizReply.cause());
            }

            var quiz = (JsonObject) createQuizReply.result();
            log.debug("Created quiz \"{}\"", quiz);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(quiz.toBuffer());
        });
    }

    private JsonObject toCreateRequest(Integer accountId, RoutingContext routingContext) {
        var request = getRequestBodyAsJson(routingContext);
        if (request == null) {
            throw new ValidationException("Request body is empty");
        }

        var deadline = request.getInstant("deadline");
        if (deadline == null) {
            throw new ValidationException("Deadline is missing");
        }

        var name = request.getString("name");
        if (StringUtils.isBlank(name)) {
            throw new ValidationException("Name is blank");
        }

        return new JsonObject()
                .put("accountId", accountId)
                .put("deadline", deadline)
                .put("name", name);
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
        var quizId = routingContext.request().getParam("quizId");

        log.debug(String.format("Get quiz \"%s\"", quizId));

        vertx.eventBus().request(GET_ONE_QUIZ_ADDRESS, quizId, quizReply -> {
            if (quizReply.failed()) {
                throw new InternalServerErrorException(String.format("Unable to get quiz \"%s\"", quizId), quizReply.cause());
            }

            var quiz = (JsonObject) quizReply.result();
            log.debug("Retrieved quiz \"{}\"", quiz);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(quiz.toBuffer());
        });
    }

    private void handleComplete(RoutingContext routingContext) {
        var quizId = routingContext.request().getParam("quizId");

        log.debug(String.format("Complete quiz \"%s\"", quizId));

        var accountId = routingContext.user().principal().getInteger("accountId");
        var completeRequest = new JsonObject()
                .put("accountId", accountId)
                .put("quizId", quizId);
        vertx.eventBus().request(COMPLETE_QUIZ_ADDRESS, quizId, completeQuizReply -> {
            if (completeQuizReply.failed()) {
                throw new InternalServerErrorException(String.format("Unable to complete quiz \"%s\"", completeRequest), completeQuizReply.cause());
            }

            log.debug("completed quiz \"{}\"", quizId);

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json")
                    .end();
        });
    }

    private void handleParticipate(RoutingContext routingContext) {
        var quizId = routingContext.request().getParam("quizId");

        log.debug("Participate in quiz \"{}\"", quizId);

        var accountId = routingContext.user().principal().getInteger("accountId");
        var participateRequest = new JsonObject()
                .put("accountId", accountId)
                .put("quizId", quizId);
        vertx.eventBus().request(PARTICIPATE_IN_QUIZ_ADDRESS, participateRequest, participateReply -> {
            if (participateReply.failed()) {
                throw new InternalServerErrorException(String.format("Unable to participate in quiz: \"%s\"", participateRequest), participateReply.cause());
            }

            var quiz = (JsonObject) participateReply.result();
            log.debug("Participating in quiz \"{}\"", quiz);

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json")
                    .end();
        });
    }
}
