package nl.friendlymirror.top10.quiz;

import static nl.friendlymirror.top10.quiz.ListEntityVerticle.*;
import static nl.friendlymirror.top10.quiz.QuizEntityVerticle.PARTICIPATE_IN_QUIZ_ADDRESS;

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
import nl.friendlymirror.top10.*;

@Log4j2
@RequiredArgsConstructor
public class ListHttpVerticle extends AbstractVerticle {

    private final Router router;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/private/quiz/:externalId/list").handler(this::handleGetAllForQuiz);
        router.route(HttpMethod.GET, "/private/list").handler(this::handleGetAllForAccount);

        router.route(HttpMethod.GET, "/private/list/:listId").handler(this::handleGetOne);

        router.route(HttpMethod.POST, "/private/list/:listId/video")
                .handler(BodyHandler.create())
                .handler(this::handleAddVideo);

        router.route(HttpMethod.PUT, "/private/list/:listId/finalize").handler(this::handleFinalize);

        router.route(HttpMethod.PUT, "/private/list/:listId/assign")
                .handler(BodyHandler.create())
                .handler(this::handleAssign);
    }

    private void handleGetAllForQuiz(RoutingContext routingContext) {
        var externalId = routingContext.pathParam("externalId");

        log.debug("Get all lists for quiz \"{}\"", externalId);

        vertx.eventBus().request(GET_ALL_LISTS_FOR_QUIZ_ADDRESS, externalId, allListsReply -> {
            if (allListsReply.failed()) {
                routingContext.fail(new InternalServerErrorException(String.format("Unable to get all lists for quiz with external ID \"%s\"", externalId), allListsReply.cause()));
                return;
            }

            var lists = (JsonArray) allListsReply.result().body();
            log.debug("Retrieved {} lists", lists.size());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(lists.toBuffer());
        });
    }

    private void handleGetAllForAccount(RoutingContext routingContext) {
        var accountId = routingContext.user().principal().getInteger("accountId");

        log.debug("Get all lists for account \"{}\"", accountId);

        vertx.eventBus().request(GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS, accountId, allListsReply -> {
            if (allListsReply.failed()) {
                routingContext.fail(new InternalServerErrorException(String.format("Unable to get all lists for account \"%s\"", accountId), allListsReply.cause()));
                return;
            }

            var lists = (JsonArray) allListsReply.result().body();
            log.debug("Retrieved {} lists", lists.size());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(lists.toBuffer());
        });
    }

    private Integer toInteger(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            log.debug("\"{}\" is not an integer", string, e);
            throw new ValidationException(String.format("\"%s\" is not an integer", string));
        }
    }

    private void handleAddVideo(RoutingContext routingContext) {
        log.debug("Add video");

        var accountId = routingContext.user().principal().getInteger("accountId");
        var listId = toInteger(routingContext.pathParam("listId"));
        var addRequest = toAddRequest(accountId, listId, routingContext);
        vertx.eventBus().request(ADD_VIDEO_ADDRESS, addRequest, addVideoReply -> {
            if (addVideoReply.failed()) {
                routingContext.fail(new InternalServerErrorException(String.format("Unable to add video \"%s\"", addRequest), addVideoReply.cause()));
                return;
            }

            var didAdd = (Boolean) addVideoReply.result().body();
            if (didAdd == false) {
                routingContext.fail(new ForbiddenException(String.format("Account \"%d\" is not allowed to add videos to list \"%d\"", accountId, listId)));
                return;
            }

            log.debug("Added video");

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("url", addRequest.getString("url"))
                            .toBuffer());
        });
    }

    private JsonObject toAddRequest(Integer accountId, Integer listId, RoutingContext routingContext) {
        var request = getRequestBodyAsJson(routingContext);
        if (request == null) {
            throw new ValidationException("Request body is empty");
        }

        var url = request.getString("url");
        if (StringUtils.isBlank(url)) {
            throw new ValidationException("URL is blank");
        }

        return new JsonObject()
                .put("accountId", accountId)
                .put("listId", listId)
                .put("url", url);
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
        var listId = toInteger(routingContext.pathParam("listId"));

        log.debug(String.format("Get list \"%s\"", listId));

        var accountId = routingContext.user().principal().getInteger("accountId");
        var getListRequest = new JsonObject()
                .put("listId", listId)
                .put("accountId", accountId);

        vertx.eventBus().request(GET_ONE_LIST_ADDRESS, getListRequest, listReply -> {
            if (listReply.failed()) {
                routingContext.fail(new InternalServerErrorException(String.format("Unable to get list \"%s\"", listId), listReply.cause()));
                return;
            }

            var list = (JsonObject) listReply.result().body();
            if (list == null) {
                routingContext.fail(new NotFoundException(String.format("List with ID \"%d\" could not be found", listId)));
                return;
            }

            log.debug("Retrieved list \"{}\"", list);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(list.toBuffer());
        });
    }

    private void handleFinalize(RoutingContext routingContext) {
        var listId = toInteger(routingContext.pathParam("listId"));

        log.debug(String.format("Finalize list \"%s\"", listId));

        var accountId = routingContext.user().principal().getInteger("accountId");
        var finalizeRequest = new JsonObject()
                .put("accountId", accountId)
                .put("listId", listId);
        vertx.eventBus().request(FINALIZE_LIST_ADDRESS, finalizeRequest, finalizeListReply -> {
            if (finalizeListReply.failed()) {
                routingContext.fail(new InternalServerErrorException(String.format("Unable to finalize list: \"%s\"", finalizeRequest), finalizeListReply.cause()));
                return;
            }

            var didFinalize = (Boolean) finalizeListReply.result().body();
            if (didFinalize == false) {
                routingContext.fail(new ForbiddenException(String.format("Account \"%d\" is not allowed to finalize list \"%d\"", accountId, listId)));
                return;
            }

            log.debug("Finalized list \"{}\"", listId);

            routingContext.response()
                    .setStatusCode(201)
                    .end();
        });
    }

    private void handleAssign(RoutingContext routingContext) {
        log.debug("Assigning list");

        var accountId = routingContext.user().principal().getInteger("accountId");
        var assignRequest = toAssignRequest(accountId, routingContext);

        vertx.eventBus().request(PARTICIPATE_IN_QUIZ_ADDRESS, assignRequest, assignReply -> {
            if (assignReply.failed()) {
                routingContext.fail(new InternalServerErrorException(String.format("Unable to assign list: \"%s\"", assignRequest), assignReply.cause()));
                return;
            }

            log.debug("Assigned list");

            routingContext.response()
                    .setStatusCode(201)
                    .end();
        });
    }

    private JsonObject toAssignRequest(Integer accountId, RoutingContext routingContext) {
        var request = getRequestBodyAsJson(routingContext);
        if (request == null) {
            throw new ValidationException("Request body is empty");
        }

        var assigneeId = toInteger(request.getString("assigneeId"));
        var listId = toInteger(routingContext.pathParam("listId"));

        return new JsonObject()
                .put("accountId", accountId)
                .put("listId", listId)
                .put("assigneeId", assigneeId);
    }
}
