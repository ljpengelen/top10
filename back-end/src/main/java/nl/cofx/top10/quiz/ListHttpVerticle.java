package nl.cofx.top10.quiz;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.ForbiddenException;
import nl.cofx.top10.InternalServerErrorException;
import nl.cofx.top10.NotFoundException;
import nl.cofx.top10.ValidationException;
import nl.cofx.top10.quiz.dto.ListDto;
import nl.cofx.top10.quiz.dto.ListsDto;
import nl.cofx.top10.url.YouTubeUrl;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;

import static nl.cofx.top10.quiz.ListEntityVerticle.*;

@Slf4j
@RequiredArgsConstructor
public class ListHttpVerticle extends AbstractVerticle {

    private final Router router;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/private/quiz/:quizId/list").handler(this::handleGetAllForQuiz);
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
        var accountId = routingContext.user().principal().getString("accountId");
        var quizId = routingContext.pathParam("quizId");

        log.debug("Get all lists for quiz \"{}\"", quizId);

        var getRequest = new JsonObject().put("accountId", accountId).put("quizId", quizId);
        vertx.eventBus().request(GET_ALL_LISTS_FOR_QUIZ_ADDRESS, getRequest, allListsReply -> {
            if (allListsReply.failed()) {
                handleFailure(allListsReply.cause(), routingContext);
                return;
            }

            var listsDto = (ListsDto) allListsReply.result().body();
            log.debug("Retrieved {} lists", listsDto.getLists().size());
            var sanitizedListsDto = sanitize(listsDto);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(sanitizedListsDto.toJsonArray().toBuffer());
        });
    }

    private ListsDto sanitize(ListsDto listsDto) {
        return listsDto.toBuilder()
                .clearLists()
                .lists(listsDto.getLists().stream()
                        .map(this::sanitize)
                        .collect(Collectors.toList()))
                .build();
    }

    private ListDto sanitize(ListDto listDto) {
        if (listDto.isActiveQuiz()) {
            return listDto.toBuilder()
                    .creatorId(null)
                    .creatorName(null)
                    .build();
        }

        return listDto;
    }

    private void handleGetAllForAccount(RoutingContext routingContext) {
        var accountId = routingContext.user().principal().getString("accountId");

        log.debug("Get all lists for account \"{}\"", accountId);

        vertx.eventBus().request(GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS, accountId, allListsReply -> {
            if (allListsReply.failed()) {
                handleFailure(allListsReply.cause(), routingContext);
                return;
            }

            var listsDto = (ListsDto) allListsReply.result().body();
            log.debug("Retrieved {} lists", listsDto.getLists().size());
            var sanitizedListsDto = sanitize(listsDto);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(sanitizedListsDto.toJsonArray().toBuffer());
        });
    }

    private void handleAddVideo(RoutingContext routingContext) {
        log.debug("Add video");

        var accountId = routingContext.user().principal().getString("accountId");
        var listId = routingContext.pathParam("listId");
        var addRequest = toAddRequest(accountId, listId, routingContext);
        vertx.eventBus().request(ADD_VIDEO_ADDRESS, addRequest, addVideoReply -> {
            if (addVideoReply.failed()) {
                handleFailure(addVideoReply.cause(), routingContext);
                return;
            }

            log.debug("Added video");

            var videoId = (String) addVideoReply.result().body();

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("id", videoId)
                            .put("url", addRequest.getString("url"))
                            .put("referenceId", addRequest.getString("referenceId"))
                            .toBuffer());
        });
    }

    private void handleDeleteVideo(RoutingContext routingContext) {
        log.debug("Delete video");

        var accountId = routingContext.user().principal().getString("accountId");
        var videoId = routingContext.pathParam("videoId");
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
                    .setStatusCode(204)
                    .end();
        });
    }

    private JsonObject toAddRequest(String accountId, String listId, RoutingContext routingContext) {
        var request = getRequestBodyAsJson(routingContext);
        if (request == null) {
            throw new ValidationException("Request body is empty");
        }

        var submittedUrl = request.getString("url");
        if (StringUtils.isBlank(submittedUrl)) {
            throw new ValidationException("URL is blank");
        }

        var url = YouTubeUrl.toEmbeddableUrl(submittedUrl);
        var referenceId = YouTubeUrl.extractVideoId(submittedUrl);

        return new JsonObject()
                .put("accountId", accountId)
                .put("listId", listId)
                .put("url", url)
                .put("referenceId", referenceId);
    }

    private JsonObject getRequestBodyAsJson(RoutingContext routingContext) {
        try {
            return routingContext.body().asJsonObject();
        } catch (Exception e) {
            log.warn("Unable to parse request body as JSON", e);
            return null;
        }
    }

    private void handleGetOne(RoutingContext routingContext) {
        var listId = routingContext.pathParam("listId");

        log.debug(String.format("Get list \"%s\"", listId));

        var accountId = routingContext.user().principal().getString("accountId");
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
            var sanitizedList = sanitize(list);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(sanitizedList.toJsonObject().toBuffer());
        });
    }

    private void handleFinalize(RoutingContext routingContext) {
        var listId = routingContext.pathParam("listId");

        log.debug(String.format("Finalize list \"%s\"", listId));

        var accountId = routingContext.user().principal().getString("accountId");
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
                    .setStatusCode(204)
                    .end();
        });
    }

    private void handleAssign(RoutingContext routingContext) {
        log.debug("Assigning list");

        var accountId = routingContext.user().principal().getString("accountId");
        var assignRequest = toAssignRequest(accountId, routingContext);

        vertx.eventBus().request(ASSIGN_LIST_ADDRESS, assignRequest, assignReply -> {
            if (assignReply.failed()) {
                handleFailure(assignReply.cause(), routingContext);
                return;
            }

            log.debug("Assigned list");

            routingContext.response()
                    .setStatusCode(204)
                    .end();
        });
    }

    private JsonObject toAssignRequest(String accountId, RoutingContext routingContext) {
        var request = getRequestBodyAsJson(routingContext);
        if (request == null) {
            throw new ValidationException("Request body is empty");
        }

        var assigneeId = request.getString("assigneeId");
        var listId = routingContext.pathParam("listId");

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
