package nl.friendlymirror.top10.quiz;

import static nl.friendlymirror.top10.quiz.ListEntityVerticle.*;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.*;
import nl.friendlymirror.top10.quiz.dto.ListDto;
import nl.friendlymirror.top10.quiz.dto.ListsDto;
import nl.friendlymirror.top10.url.YouTubeUrl;

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
        router.route(HttpMethod.DELETE, "/private/video/:videoId").handler(this::handleDeleteVideo);

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
                handleFailure(allListsReply.cause(), routingContext);
                return;
            }

            var listsDto = (ListsDto) allListsReply.result().body();
            log.debug("Retrieved {} lists", listsDto.getLists().size());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(listsDto.toJsonArray().toBuffer());
        });
    }

    private void handleGetAllForAccount(RoutingContext routingContext) {
        var accountId = routingContext.user().principal().getInteger("accountId");

        log.debug("Get all lists for account \"{}\"", accountId);

        vertx.eventBus().request(GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS, accountId, allListsReply -> {
            if (allListsReply.failed()) {
                handleFailure(allListsReply.cause(), routingContext);
                return;
            }

            var listsDto = (ListsDto) allListsReply.result().body();
            log.debug("Retrieved {} lists", listsDto.getLists().size());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(listsDto.toJsonArray().toBuffer());
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
                handleFailure(addVideoReply.cause(), routingContext);
                return;
            }

            log.debug("Added video");

            var videoId = (Integer) addVideoReply.result().body();

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("id", videoId)
                            .put("url", addRequest.getString("url"))
                            .toBuffer());
        });
    }

    private void handleDeleteVideo(RoutingContext routingContext) {
        log.debug("Delete video");

        var accountId = routingContext.user().principal().getInteger("accountId");
        var videoId = toInteger(routingContext.pathParam("videoId"));
        var deleteRequest = new JsonObject()
                .put("accountId", accountId)
                .put("videoId", videoId);
        vertx.eventBus().request(DELETE_VIDEO_ADDRESS, deleteRequest, deleteVideoReply -> {
            if (deleteVideoReply.failed()) {
                handleFailure(deleteVideoReply.cause(), routingContext);
                return;
            }

            log.debug("Deleted video");

            routingContext.response()
                    .setStatusCode(201)
                    .end();
        });
    }

    private JsonObject toAddRequest(Integer accountId, Integer listId, RoutingContext routingContext) {
        var request = getRequestBodyAsJson(routingContext);
        if (request == null) {
            throw new ValidationException("Request body is empty");
        }

        var submittedUrl = request.getString("url");
        if (StringUtils.isBlank(submittedUrl)) {
            throw new ValidationException("URL is blank");
        }

        var url = YouTubeUrl.toEmbeddableUrl(submittedUrl);

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
                handleFailure(listReply.cause(), routingContext);
                return;
            }

            var list = (ListDto) listReply.result().body();
            log.debug("Retrieved list \"{}\"", list);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(list.toJsonObject().toBuffer());
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
                handleFailure(finalizeListReply.cause(), routingContext);
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

        vertx.eventBus().request(ASSIGN_LIST_ADDRESS, assignRequest, assignReply -> {
            if (assignReply.failed()) {
                handleFailure(assignReply.cause(), routingContext);
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

        var assigneeId = request.getString("assigneeId");
        var listId = toInteger(routingContext.pathParam("listId"));

        return new JsonObject()
                .put("accountId", accountId)
                .put("listId", listId)
                .put("assigneeId", assigneeId);
    }

    private void handleFailure(Throwable cause, RoutingContext routingContext) {
        var replyException = (ReplyException) cause;
        if (replyException.failureCode() == 404) {
            routingContext.fail(new NotFoundException(replyException.getMessage()));
        } else if (replyException.failureCode() == 403) {
            routingContext.fail(new ForbiddenException(replyException.getMessage()));
        } else {
            routingContext.fail(new InternalServerErrorException(replyException.getMessage(), replyException));
        }
    }
}
