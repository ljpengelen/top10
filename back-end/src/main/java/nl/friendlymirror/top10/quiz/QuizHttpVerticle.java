package nl.friendlymirror.top10.quiz;

import static nl.friendlymirror.top10.quiz.QuizEntityVerticle.*;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.random.RandomDataGenerator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.*;

@Log4j2
@RequiredArgsConstructor
public class QuizHttpVerticle extends AbstractVerticle {

    private final RandomDataGenerator randomDataGenerator = new RandomDataGenerator();

    private final Router router;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/private/quiz").handler(this::handleGetAll);

        router.route(HttpMethod.POST, "/private/quiz")
                .handler(BodyHandler.create())
                .handler(this::handleCreate);

        router.route(HttpMethod.GET, "/private/quiz/:externalId").handler(this::handleGetOne);

        router.route(HttpMethod.PUT, "/private/quiz/:externalId/complete").handler(this::handleComplete);

        router.route(HttpMethod.PUT, "/private/quiz/:externalId/participate").handler(this::handleParticipate);
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

            log.debug("Created quiz");

            routingContext.response()
                    .setStatusCode(201)
                    .end();
        });
    }

    private JsonObject toCreateRequest(Integer accountId, RoutingContext routingContext) {
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
                .put("externalId", randomDataGenerator.nextSecureHexString(32))
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
        var externalId = routingContext.request().getParam("externalId");

        log.debug(String.format("Get quiz \"%s\"", externalId));

        vertx.eventBus().request(GET_ONE_QUIZ_ADDRESS, externalId, quizReply -> {
            if (quizReply.failed()) {
                throw new InternalServerErrorException(String.format("Unable to get quiz with external ID \"%s\"", externalId), quizReply.cause());
            }

            var quiz = (JsonObject) quizReply.result().body();
            log.debug("Retrieved quiz \"{}\"", quiz);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(quiz.toBuffer());
        });
    }

    private void handleComplete(RoutingContext routingContext) {
        var externalId = routingContext.request().getParam("externalId");

        log.debug(String.format("Complete quiz with external ID \"%s\"", externalId));

        var accountId = routingContext.user().principal().getInteger("accountId");
        var completeRequest = new JsonObject()
                .put("accountId", accountId)
                .put("externalId", externalId);
        vertx.eventBus().request(COMPLETE_QUIZ_ADDRESS, completeRequest, completeQuizReply -> {
            if (completeQuizReply.failed()) {
                throw new InternalServerErrorException(String.format("Unable to complete quiz: \"%s\"", completeRequest), completeQuizReply.cause());
            }

            var didComplete = (Boolean) completeQuizReply.result().body();
            if (didComplete == false) {
                routingContext.fail(new ForbiddenException(String.format("Account \"%s\" is not allowed to complete quiz \"%s\"", accountId, externalId)));
                return;
            }

            log.debug("Completed quiz with external ID \"{}\"", externalId);

            routingContext.response()
                    .setStatusCode(201)
                    .end();
        });
    }

    private void handleParticipate(RoutingContext routingContext) {
        var externalId = routingContext.request().getParam("externalId");

        log.debug("Participate in quiz with external ID \"{}\"", externalId);

        var accountId = routingContext.user().principal().getInteger("accountId");
        var participateRequest = new JsonObject()
                .put("accountId", accountId)
                .put("externalId", externalId);
        vertx.eventBus().request(PARTICIPATE_IN_QUIZ_ADDRESS, participateRequest, participateReply -> {
            if (participateReply.failed()) {
                throw new InternalServerErrorException(String.format("Unable to participate in quiz: \"%s\"", participateRequest), participateReply.cause());
            }

            log.debug("Participating in quiz with external ID \"{}\"", externalId);

            routingContext.response()
                    .setStatusCode(201)
                    .end();
        });
    }
}
