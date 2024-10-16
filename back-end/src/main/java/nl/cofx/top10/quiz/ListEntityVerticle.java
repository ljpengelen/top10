package nl.cofx.top10.quiz;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.ForbiddenException;
import nl.cofx.top10.NotFoundException;
import nl.cofx.top10.entity.AbstractEntityVerticle;
import nl.cofx.top10.quiz.dto.ListDto;
import nl.cofx.top10.quiz.dto.ListsDto;

import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
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
    private final QuizRepository quizRepository = new QuizRepository();

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

    private void handleGetAll(Message<JsonObject> getAllListsRequest) {
        var body = getAllListsRequest.body();
        var quizId = body.getString("quizId");
        var accountId = body.getString("accountId");

        withTransaction(connection ->
                listRepository.getAllListsForQuiz(connection, quizId, accountId).compose(listDtos -> {
                    var listIds = listDtos.stream()
                            .map(ListDto::getId)
                            .collect(Collectors.toList());
                    return listRepository.getAssignments(connection, accountId, listIds)
                            .compose(assignmentsForLists -> Future.succeededFuture(listDtos.stream()
                                    .map(listDto -> {
                                        var listId = listDto.getId();
                                        var assignment = assignmentsForLists.get(listId);
                                        if (assignment != null) {
                                            return listDto.toBuilder()
                                                    .assigneeName(assignment.getAssigneeName())
                                                    .assigneeId(assignment.getAssigneeId())
                                                    .build();
                                        } else {
                                            return listDto;
                                        }
                                    }).collect(Collectors.toList())));
                }))
                .onSuccess(listDtos ->
                        getAllListsRequest.reply(ListsDto.builder()
                                .lists(listDtos)
                                .build()))
                .onFailure(cause -> handleFailure(cause, getAllListsRequest));
    }

    private void handleGetAllForAccount(Message<String> getAllListsForAccountRequest) {
        var accountId = getAllListsForAccountRequest.body();
        withTransaction(connection -> listRepository.getAllListsForAccount(connection, accountId)
                .compose(listDtos -> {
                    var listIds = listDtos.stream()
                            .map(ListDto::getId)
                            .collect(Collectors.toList());
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
        var listId = body.getString("listId");
        var accountId = body.getString("accountId");

        withTransaction(connection -> listRepository.getList(connection, listId, accountId)
                .compose(list ->
                        listRepository.getAssignments(connection, accountId, Collections.singletonList(listId)).compose(assignments -> {
                            var assignment = assignments.get(listId);
                            if (assignment != null) {
                                return Future.succeededFuture(list.toBuilder()
                                        .assigneeId(assignments.get(listId).getAssigneeId())
                                        .assigneeName(assignments.get(listId).getAssigneeName())
                                        .build());
                            } else {
                                return Future.succeededFuture(list);
                            }
                        })).compose(list ->
                        listRepository.validateAccountCanAccessList(connection, accountId, listId).compose(accountCanAccessList ->
                                listRepository.getVideosForLists(connection, Collections.singletonList(list.getId())).compose(videosForList ->
                                        Future.succeededFuture(list.toBuilder()
                                                .videos(videosForList.getOrDefault(listId, Collections.emptyList()))
                                                .build())))))
                .onSuccess(getOneListRequest::reply)
                .onFailure(cause -> handleFailure(cause, getOneListRequest));
    }

    private void handleAddVideo(Message<JsonObject> addVideoRequest) {
        var body = addVideoRequest.body();
        var listId = body.getString("listId");
        var url = body.getString("url");
        var referenceId = body.getString("referenceId");
        var accountId = body.getString("accountId");

        withTransaction(connection ->
                listRepository.getList(connection, listId, accountId).compose(listDto -> {
                    if (!accountId.equals(listDto.getCreatorId())) {
                        var message = String.format("Account \"%s\" did not create list \"%s\"", accountId, listId);
                        log.debug(message);
                        return Future.failedFuture(new ForbiddenException(message));
                    } else if (!listDto.getHasDraftStatus()) {
                        log.debug("Account \"{}\" cannot add video to finalized list \"{}\"", accountId, listId);
                        return Future.failedFuture(new ForbiddenException(String.format("List \"%s\" is finalized", listId)));
                    } else {
                        var quizId = listDto.getQuizId();
                        return quizRepository.getQuiz(connection, quizId, accountId).compose(quizDto -> {
                            if (!quizDto.isActive()) {
                                log.debug("Quiz \"{}\" has ended", quizId);
                                return Future.failedFuture(new ForbiddenException(String.format("Quiz \"%s\" has ended", quizId)));
                            } else if (quizDto.getDeadline().isBefore(Instant.now())) {
                                log.debug("Deadline for quiz \"{}\" has passed", quizId);
                                return Future.failedFuture(new ForbiddenException(String.format("Deadline for quiz \"%s\" has passed", quizId)));
                            } else {
                                return listRepository.addVideo(connection, listId, url, referenceId);
                            }
                        });
                    }
                }))
                .onSuccess(addVideoRequest::reply)
                .onFailure(cause -> handleFailure(cause, addVideoRequest));
    }

    private void handleDeleteVideo(Message<JsonObject> deleteVideoRequest) {
        var body = deleteVideoRequest.body();
        var videoId = body.getString("videoId");
        var accountId = body.getString("accountId");

        withTransaction(connection ->
                listRepository.getListByVideoId(connection, videoId).compose(listDto -> {
                    var listId = listDto.getId();
                    if (!accountId.equals(listDto.getCreatorId())) {
                        var message = String.format("Account \"%s\" did not create list \"%s\"", accountId, listId);
                        log.debug(message);
                        return Future.failedFuture(new ForbiddenException(message));
                    } else if (!listDto.getHasDraftStatus()) {
                        log.debug("Account \"{}\" cannot delete video from finalized list \"{}\"", accountId, listId);
                        return Future.failedFuture(new ForbiddenException(String.format("List \"%s\" is finalized", listId)));
                    } else {
                        return listRepository.deleteVideo(connection, videoId);
                    }
                }))
                .onSuccess(deleteVideoRequest::reply)
                .onFailure(cause -> handleFailure(cause, deleteVideoRequest));
    }

    private void handleFinalizeList(Message<JsonObject> finalizeListRequest) {
        var body = finalizeListRequest.body();
        var accountId = body.getString("accountId");
        var listId = body.getString("listId");

        withTransaction(connection ->
                listRepository.getList(connection, listId, accountId).compose(listDto -> {
                    if (!accountId.equals(listDto.getCreatorId())) {
                        return Future.failedFuture(new ForbiddenException(String.format("Account \"%s\" did not create list \"%s\"", accountId, listId)));
                    } else {
                        var quizId = listDto.getQuizId();
                        return quizRepository.getQuiz(connection, quizId, accountId).compose(quizDto -> {
                            if (quizDto.getDeadline().isBefore(Instant.now())) {
                                log.debug("Deadline for quiz \"{}\" has passed", quizId);
                                return Future.failedFuture(new ForbiddenException(String.format("Deadline for quiz \"%s\" has passed", quizId)));
                            } else if (!quizDto.isActive()) {
                                log.debug("Quiz \"{}\" has ended", quizId);
                                return Future.failedFuture(new ForbiddenException(String.format("Quiz \"%s\" has ended", quizId)));
                            } else {
                                return listRepository.finalizeList(connection, listId);
                            }
                        });
                    }
                }))
                .onSuccess(finalizeListRequest::reply)
                .onFailure(cause -> handleFailure(cause, finalizeListRequest));
    }

    private void handleAssignList(Message<JsonObject> assignListRequest) {
        var body = assignListRequest.body();
        var accountId = body.getString("accountId");
        var listId = body.getString("listId");
        var assigneeId = body.getString("assigneeId");

        withTransaction(connection ->
                listRepository.getList(connection, listId, accountId).compose(listDto -> {
                    if (listDto.getHasDraftStatus()) {
                        log.debug("User \"{}\" cannot assign \"{}\" to non-finalized list \"{}\"", accountId, assigneeId, listId);
                        return Future.failedFuture(new ForbiddenException(String.format("List \"%s\" has not been finalized yet", listId)));
                    } else {
                        var quizId = listDto.getQuizId();
                        return quizRepository.getQuiz(connection, quizId, accountId).compose(quiz -> {
                            if (quiz.isActive()) {
                                return listRepository.validateAccountCanAccessList(connection, accountId, listId).compose(accountCanAccessList ->
                                        listRepository.validateAccountParticipatesInQuiz(connection, assigneeId, quizId)
                                                .compose(accountParticipatesInQuiz ->
                                                        listRepository.assignList(connection, accountId, listId, assigneeId)));
                            } else {
                                log.debug("User \"{}\" cannot assign to list \"{}\" of completed quiz \"{}\"", accountId, listId, quizId);
                                return Future.failedFuture(new ForbiddenException(String.format("Quiz \"%s\" is completed", quizId)));
                            }
                        });
                    }
                }))
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
            log.error("An unexpected error occurred: " + errorMessage);
            message.fail(500, errorMessage);
        }
    }
}
