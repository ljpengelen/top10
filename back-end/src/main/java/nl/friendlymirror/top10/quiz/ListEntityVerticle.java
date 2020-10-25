package nl.friendlymirror.top10.quiz;

import java.util.Collections;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.ForbiddenException;
import nl.friendlymirror.top10.NotFoundException;
import nl.friendlymirror.top10.entity.AbstractEntityVerticle;
import nl.friendlymirror.top10.quiz.dto.ListDto;
import nl.friendlymirror.top10.quiz.dto.ListsDto;

@Log4j2
@RequiredArgsConstructor
public class ListEntityVerticle extends AbstractEntityVerticle {

    public static final String GET_ALL_LISTS_FOR_QUIZ_ADDRESS = "entity.list.getAllForQuiz";
    public static final String GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS = "entity.list.getAllForAccount";
    public static final String GET_ONE_LIST_ADDRESS = "entity.list.getOne";
    public static final String ADD_VIDEO_ADDRESS = "entity.list.addVideo";
    public static final String DELETE_VIDEO_ADDRESS = "entity.list.deleteVideo";
    public static final String FINALIZE_LIST_ADDRESS = "entity.list.finalize";
    public static final String ASSIGN_LIST_ADDRESS = "entity.list.assign";

    private final ListRepository listRepository = new ListRepository();

    private final JsonObject jdbcOptions;

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        vertx.eventBus().consumer(GET_ALL_LISTS_FOR_QUIZ_ADDRESS, this::handleGetAll);
        vertx.eventBus().consumer(GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS, this::handleGetAllForAccount);
        vertx.eventBus().consumer(GET_ONE_LIST_ADDRESS, this::handleGetOne);
        vertx.eventBus().consumer(ADD_VIDEO_ADDRESS, this::handleAddVideo);
        vertx.eventBus().consumer(DELETE_VIDEO_ADDRESS, this::handleDeleteVideo);
        vertx.eventBus().consumer(FINALIZE_LIST_ADDRESS, this::handleFinalizeList);
        vertx.eventBus().consumer(ASSIGN_LIST_ADDRESS, this::handleAssignList);
    }

    private void handleGetAll(Message<String> getAllListsRequest) {
        var externalId = getAllListsRequest.body();

        withTransaction(connection -> listRepository.getAllListsForQuiz(connection, externalId))
                .onSuccess(getAllListsRequest::reply)
                .onFailure(cause -> handleFailure(cause, getAllListsRequest));
    }

    private void handleGetAllForAccount(Message<Integer> getAllListsForAccountRequest) {
        var accountId = getAllListsForAccountRequest.body();
        withTransaction(connection -> listRepository.getAllListsForAccount(connection, accountId)
                .compose(listDtos -> {
                    var listIds = listDtos.stream()
                            .map(ListDto::getId)
                            .mapToInt(i -> i)
                            .toArray();
                    return listRepository.getVideosForLists(connection, listIds)
                            .compose(videosForList -> Future.succeededFuture(listDtos.stream()
                                    .map(listDto -> listDto.toBuilder()
                                            .videos(videosForList.get(listDto.getId()))
                                            .build())
                                    .collect(Collectors.toList())));
                }))
                .onSuccess(listDtos -> getAllListsForAccountRequest.reply(ListsDto.builder().lists(listDtos).build()))
                .onFailure(cause -> handleFailure(cause, getAllListsForAccountRequest));
    }

    private void handleGetOne(Message<JsonObject> getOneListRequest) {
        var body = getOneListRequest.body();
        var listId = body.getInteger("listId");
        var accountId = body.getInteger("accountId");

        withTransaction(connection -> listRepository.getList(connection, listId).compose(list ->
                listRepository.validateAccountCanAccessList(connection, accountId, listId).compose(accountCanAccessList ->
                        listRepository.getVideosForLists(connection, list.getId()).compose(videosForList ->
                                Future.succeededFuture(list.toBuilder()
                                        .videos(videosForList.getOrDefault(listId, Collections.emptyList()))
                                        .build())))))
                .onSuccess(getOneListRequest::reply)
                .onFailure(cause -> handleFailure(cause, getOneListRequest));
    }

    private void handleAddVideo(Message<JsonObject> addVideoRequest) {
        var body = addVideoRequest.body();
        var listId = body.getInteger("listId");
        var url = body.getString("url");
        var accountId = body.getInteger("accountId");

        withTransaction(connection ->
                listRepository.getList(connection, listId).compose(listDto -> {
                    if (accountId.equals(listDto.getAccountId())) {
                        return listRepository.addVideo(connection, listId, url);
                    } else {
                        return Future.failedFuture(new ForbiddenException(String.format("Account \"%d\" did not create list \"%d\"", accountId, listId)));
                    }
                }))
                .onSuccess(addVideoRequest::reply)
                .onFailure(cause -> handleFailure(cause, addVideoRequest));
    }

    private void handleDeleteVideo(Message<JsonObject> deleteVideoRequest) {
        var body = deleteVideoRequest.body();
        var videoId = body.getInteger("videoId");
        var accountId = body.getInteger("accountId");

        withTransaction(connection ->
                listRepository.getListByVideoId(connection, videoId).compose(listDto -> {
                    if (accountId.equals(listDto.getAccountId())) {
                        return listRepository.deleteVideo(connection, videoId);
                    } else {
                        return Future.failedFuture(new ForbiddenException(String.format("Account \"%d\" did not create list \"%d\"", accountId, listDto.getId())));
                    }
                }))
                .onSuccess(deleteVideoRequest::reply)
                .onFailure(cause -> handleFailure(cause, deleteVideoRequest));
    }

    private void handleFinalizeList(Message<JsonObject> finalizeListRequest) {
        var body = finalizeListRequest.body();
        var accountId = body.getInteger("accountId");
        var listId = body.getInteger("listId");

        withTransaction(connection ->
                listRepository.getList(connection, listId).compose(listDto -> {
                    if (accountId.equals(listDto.getAccountId())) {
                        return listRepository.finalizeList(connection, listId);
                    } else {
                        return Future.failedFuture(new ForbiddenException(String.format("Account \"%d\" did not create list \"%d\"", accountId, listId)));
                    }
                }))
                .onSuccess(finalizeListRequest::reply)
                .onFailure(cause -> handleFailure(cause, finalizeListRequest));
    }

    private void handleAssignList(Message<JsonObject> assignListRequest) {
        var body = assignListRequest.body();
        var accountId = body.getInteger("accountId");
        var listId = body.getInteger("listId");
        var assigneeId = body.getInteger("assigneeId");

        withTransaction(connection ->
                listRepository.getList(connection, listId).compose(listDto ->
                        listRepository.validateAccountCanAccessList(connection, accountId, listId).compose(accountCanAccessList ->
                                listRepository.validateAccountParticipatesInQuiz(connection, assigneeId, listDto.getQuizId()).compose(accountParticipatesInQuiz ->
                                        listRepository.assignList(connection, accountId, listId, assigneeId)))))
                .onSuccess(assignListRequest::reply)
                .onFailure(cause -> handleFailure(cause, assignListRequest));
    }

    private <T> void handleFailure(Throwable cause, Message<T> message) {
        var errorMessage = cause.getMessage();
        if (cause instanceof ForbiddenException) {
            message.fail(403, errorMessage);
        } else if (cause instanceof NotFoundException) {
            message.fail(404, errorMessage);
        } else {
            message.fail(500, errorMessage);
        }
    }
}
