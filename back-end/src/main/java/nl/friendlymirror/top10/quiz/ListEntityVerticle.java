package nl.friendlymirror.top10.quiz;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.ForbiddenException;
import nl.friendlymirror.top10.NotFoundException;
import nl.friendlymirror.top10.entity.AbstractEntityVerticle;
import nl.friendlymirror.top10.quiz.dto.*;

@Log4j2
@RequiredArgsConstructor
public class ListEntityVerticle extends AbstractEntityVerticle {

    public static final String GET_ALL_LISTS_FOR_QUIZ_ADDRESS = "entity.list.getAllForQuiz";
    public static final String GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS = "entity.list.getAllForAccount";
    public static final String GET_ONE_LIST_ADDRESS = "entity.list.getOne";
    public static final String ADD_VIDEO_ADDRESS = "entity.list.addVideo";
    public static final String FINALIZE_LIST_ADDRESS = "entity.list.finalize";
    public static final String ASSIGN_LIST_ADDRESS = "entity.list.assign";

    private static final String GET_ALL_LISTS_FOR_QUIZ_TEMPLATE = "SELECT l.list_id, a.assignee_id FROM list l "
                                                                  + "JOIN quiz q ON l.quiz_id = q.quiz_id "
                                                                  + "LEFT JOIN assignment a ON l.list_id = a.list_id "
                                                                  + "WHERE q.external_id = ? AND NOT l.has_draft_status";
    private static final String GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE = "SELECT l.list_id FROM list l WHERE l.account_id = ?";
    private static final String GET_VIDEOS_FOR_LISTS_TEMPLATE = "SELECT v.video_id, v.list_id, v.url FROM video v WHERE v.list_id = ANY (?)";
    private static final String ACCOUNT_CAN_ACCESS_LIST_TEMPLATE = "SELECT COUNT(l.list_id) from list l "
                                                                   + "JOIN participant p ON l.quiz_id = p.quiz_id "
                                                                   + "WHERE p.account_id = ? AND l.list_id = ?";
    private static final String ACCOUNT_PARTICIPATES_IN_QUIZ_TEMPLATE = "SELECT COUNT(p.account_id) FROM quiz q "
                                                                        + "JOIN participant p ON q.quiz_id = p.quiz_id "
                                                                        + "WHERE p.account_id = ? AND q.quiz_id = ?";
    private static final String GET_ONE_LIST_TEMPLATE = "SELECT l.list_id, l.has_draft_status, a.assignee_id, l.quiz_id, l.account_id FROM video v "
                                                        + "NATURAL RIGHT JOIN list l "
                                                        + "LEFT JOIN assignment a ON l.list_id = a.list_id "
                                                        + "WHERE l.list_id = ?";
    private static final String ADD_VIDEO_TEMPLATE = "INSERT INTO video (list_id, url) VALUES (?, ?) ON CONFLICT DO NOTHING";
    private static final String FINALIZE_LIST_TEMPLATE = "UPDATE list SET has_draft_status = false WHERE list_id = ? and account_id = ?";
    private static final String ASSIGN_LIST_TEMPLATE = "INSERT INTO assignment (list_id, account_id, assignee_id) VALUES (?, ?, ?) "
                                                       + "ON CONFLICT (list_id, account_id) DO "
                                                       + "UPDATE SET assignee_id = EXCLUDED.assignee_id";

    private final JsonObject jdbcOptions;

    @Override
    public void start() {
        log.info("Starting");

        sqlClient = JDBCClient.createShared(vertx, jdbcOptions);

        vertx.eventBus().consumer(GET_ALL_LISTS_FOR_QUIZ_ADDRESS, this::handleGetAll);
        vertx.eventBus().consumer(GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS, this::handleGetAllForAccount);
        vertx.eventBus().consumer(GET_ONE_LIST_ADDRESS, this::handleGetOne);
        vertx.eventBus().consumer(ADD_VIDEO_ADDRESS, this::handleAddVideo);
        vertx.eventBus().consumer(FINALIZE_LIST_ADDRESS, this::handleFinalizeList);
        vertx.eventBus().consumer(ASSIGN_LIST_ADDRESS, this::handleAssignList);
    }

    private void handleGetAll(Message<String> getAllListsRequest) {
        var externalId = getAllListsRequest.body();

        withTransaction(connection -> getAllListsForQuiz(connection, externalId))
                .onSuccess(getAllListsRequest::reply)
                .onFailure(cause -> getAllListsRequest.fail(500, cause.getMessage()));
    }

    private Future<ListsDto> getAllListsForQuiz(SQLConnection connection, String externalId) {
        var promise = Promise.<ListsDto> promise();

        connection.queryWithParams(GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, new JsonArray().add(externalId), asyncLists -> {
            if (asyncLists.failed()) {
                var cause = asyncLists.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, externalId, cause);
                promise.fail(cause);
                return;
            }

            log.debug("Retrieved all lists for quiz with external ID \"{}\"", externalId);

            var listDtos = asyncLists.result().getRows().stream()
                    .map(row -> ListDto.builder()
                            .listId(row.getInteger("list_id"))
                            .assigneeId(row.getInteger("assignee_id"))
                            .build())
                    .collect(Collectors.toList());

            promise.complete(ListsDto.builder()
                    .lists(listDtos)
                    .build());
        });

        return promise.future();
    }

    private void handleGetAllForAccount(Message<Integer> getAllListsForAccountRequest) {
        var accountId = getAllListsForAccountRequest.body();
        withTransaction(connection -> getAllListsForAccount(connection, accountId)
                .compose(listDtos -> {
                    var listIds = listDtos.stream()
                            .map(ListDto::getListId)
                            .mapToInt(i -> i)
                            .toArray();
                    return getVideosForLists(connection, listIds)
                            .compose(videosForList -> Future.succeededFuture(listDtos.stream()
                                    .map(listDto -> listDto.toBuilder()
                                            .videos(videosForList.get(listDto.getListId()))
                                            .build())
                                    .collect(Collectors.toList())));
                }))
                .onSuccess(listDtos -> getAllListsForAccountRequest.reply(ListsDto.builder().lists(listDtos).build()))
                .onFailure(cause -> getAllListsForAccountRequest.fail(500, cause.getMessage()));
    }

    private Future<List<ListDto>> getAllListsForAccount(SQLConnection connection, Integer accountId) {
        var promise = Promise.<List<ListDto>> promise();

        connection.queryWithParams(GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE, new JsonArray().add(accountId), asyncLists -> {
            if (asyncLists.failed()) {
                var cause = asyncLists.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE, accountId, cause);
                promise.fail(cause);
                return;
            }

            log.debug("Retrieved all lists for account");

            var listDtos = asyncLists.result().getRows().stream()
                    .map(row -> ListDto.builder()
                            .listId(row.getInteger("list_id"))
                            .build())
                    .collect(Collectors.toList());

            promise.complete(listDtos);
        });

        return promise.future();
    }

    private Future<Map<Integer, List<VideoDto>>> getVideosForLists(SQLConnection connection, int... listIds) {
        var promise = Promise.<Map<Integer, List<VideoDto>>> promise();

        connection.queryWithParams(GET_VIDEOS_FOR_LISTS_TEMPLATE, new JsonArray().add(listIds), asyncVideos -> {
            if (asyncVideos.failed()) {
                var cause = asyncVideos.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_VIDEOS_FOR_LISTS_TEMPLATE, listIds, cause);
                promise.fail(cause);
                return;
            }

            log.debug("Retrieved all videos for lists");

            Map<Integer, List<VideoDto>> videosForLists = IntStream.of(listIds).boxed()
                    .collect(Collectors.toMap(Function.identity(), i -> new ArrayList<>()));

            asyncVideos.result().getRows().forEach(row -> {
                var listId = row.getInteger("list_id");
                var videoDto = VideoDto.builder()
                        .videoId(row.getInteger("video_id"))
                        .url(row.getString("url"))
                        .build();
                videosForLists.get(listId).add(videoDto);
            });

            promise.complete(videosForLists);
        });

        return promise.future();
    }

    private void handleGetOne(Message<JsonObject> getOneListRequest) {
        var body = getOneListRequest.body();
        var listId = body.getInteger("listId");
        var accountId = body.getInteger("accountId");

        withTransaction(connection -> getList(connection, listId).compose(list ->
                validateAccountCanAccessList(connection, accountId, listId).compose(accountCanAccessList ->
                        getVideosForLists(connection, list.getListId()).compose(videosForList ->
                                Future.succeededFuture(list.toBuilder()
                                        .videos(videosForList.getOrDefault(listId, Collections.emptyList()))
                                        .build())))))
                .onSuccess(getOneListRequest::reply)
                .onFailure(cause -> {
                    if (cause instanceof NotFoundException) {
                        getOneListRequest.fail(404, cause.getMessage());
                    } else if (cause instanceof ForbiddenException) {
                        getOneListRequest.fail(403, cause.getMessage());
                    } else {
                        getOneListRequest.fail(500, cause.getMessage());
                    }
                });
    }

    private Future<ListDto> getList(SQLConnection connection, Integer listId) {
        var promise = Promise.<ListDto> promise();

        connection.querySingleWithParams(GET_ONE_LIST_TEMPLATE, new JsonArray().add(listId), asyncList -> {
            if (asyncList.failed()) {
                var cause = asyncList.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_ONE_LIST_TEMPLATE, listId, cause);
                promise.fail(cause);
                return;
            }

            log.debug("Retrieved list");

            var row = asyncList.result();
            if (row == null) {
                log.debug("List \"{}\" not found", listId);
                promise.fail(new NotFoundException(String.format("List \"%d\" not found", listId)));
            } else {
                var listDto = ListDto.builder()
                        .listId(row.getInteger(0))
                        .hasDraftStatus(row.getBoolean(1))
                        .assigneeId(row.getInteger(2))
                        .quizId(row.getInteger(3))
                        .accountId(row.getInteger(4))
                        .build();
                log.debug("Retrieved list by ID \"{}\": \"{}\"", listId, listDto);
                promise.complete(listDto);
            }
        });

        return promise.future();
    }

    private void handleAddVideo(Message<JsonObject> addVideoRequest) {
        var body = addVideoRequest.body();
        var listId = body.getInteger("listId");
        var url = body.getString("url");
        var accountId = body.getInteger("accountId");

        withTransaction(connection ->
                getList(connection, listId).compose(listDto -> {
                    if (accountId.equals(listDto.getAccountId())) {
                        return addVideo(connection, listId, url);
                    } else {
                        return Future.failedFuture(new ForbiddenException(String.format("Account \"%d\" did not create list \"%d\"", accountId, listId)));
                    }
                }))
                .onSuccess(addVideoRequest::reply)
                .onFailure(cause -> {
                    if (cause instanceof NotFoundException) {
                        addVideoRequest.fail(404, cause.getMessage());
                    } else if (cause instanceof ForbiddenException) {
                        addVideoRequest.fail(403, cause.getMessage());
                    } else {
                        addVideoRequest.fail(500, cause.getMessage());
                    }
                });
    }

    private Future<Void> addVideo(SQLConnection connection, Integer listId, String url) {
        var promise = Promise.<Void> promise();

        connection.updateWithParams(ADD_VIDEO_TEMPLATE, new JsonArray().add(listId).add(url), asyncAddVideo -> {
            if (asyncAddVideo.failed()) {
                var cause = asyncAddVideo.cause();
                log.error("Unable to execute query \"{}\" with parameters \"{}\" and \"{}\"", ADD_VIDEO_TEMPLATE, listId, url, cause);
                promise.fail(cause);
                return;
            }

            var numberOfAffectedRows = asyncAddVideo.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Added video");
            } else {
                log.debug("Unable to add video");
            }

            promise.complete();
        });

        return promise.future();
    }

    private void handleFinalizeList(Message<JsonObject> finalizeListRequest) {
        var body = finalizeListRequest.body();
        var accountId = body.getInteger("accountId");
        var listId = body.getInteger("listId");

        sqlClient.updateWithParams(FINALIZE_LIST_TEMPLATE, new JsonArray().add(listId).add(accountId), asyncFinalize -> {
            if (asyncFinalize.failed()) {
                log.error("Unable to finalize list \"{}\"", listId, asyncFinalize.cause());
                finalizeListRequest.fail(500, "Unable to finalize list");
                return;
            }

            var numberOfAffectedRows = asyncFinalize.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Finalized list");
            } else {
                log.debug("Unable to finalize list");
            }

            finalizeListRequest.reply(numberOfAffectedRows > 0);
        });
    }

    private void handleAssignList(Message<JsonObject> assignListRequest) {
        var body = assignListRequest.body();
        var accountId = body.getInteger("accountId");
        var listId = body.getInteger("listId");
        var assigneeId = body.getInteger("assigneeId");

        withTransaction(connection ->
                getList(connection, listId).compose(listDto ->
                        validateAccountCanAccessList(connection, accountId, listId).compose(accountCanAccessList ->
                                validateAccountParticipatesInQuiz(connection, assigneeId, listDto.getQuizId()).compose(accountParticipatesInQuiz ->
                                        assignList(connection, accountId, listId, assigneeId)))))
                .onSuccess(assignListRequest::reply)
                .onFailure(cause -> {
                    if (cause instanceof NotFoundException) {
                        assignListRequest.fail(404, cause.getMessage());
                    } else if (cause instanceof ForbiddenException) {
                        assignListRequest.fail(403, cause.getMessage());
                    } else {
                        assignListRequest.fail(500, cause.getMessage());
                    }
                });
    }

    private Future<Void> assignList(SQLConnection connection, Integer accountId, Integer listId, Integer assigneeId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(listId).add(accountId).add(assigneeId);
        connection.updateWithParams(ASSIGN_LIST_TEMPLATE, parameters, asyncAssignment -> {
            if (asyncAssignment.failed()) {
                var cause = asyncAssignment.cause();
                log.error("Unable to execute query \"{}\" with parameters \"{}\"", ASSIGN_LIST_TEMPLATE, parameters, cause);
                promise.fail(cause);
                return;
            }

            var numberOfAffectedRows = asyncAssignment.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Assigned list");
            } else {
                log.debug("Unable to assign list");
            }

            promise.complete();
        });

        return promise.future();
    }

    private Future<Void> validateAccountCanAccessList(SQLConnection connection, Integer accountId, Integer listId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(accountId).add(listId);
        connection.querySingleWithParams(ACCOUNT_CAN_ACCESS_LIST_TEMPLATE, parameters, asyncCanAccess -> {
            if (asyncCanAccess.failed()) {
                var cause = asyncCanAccess.cause();
                log.error("Unable to execute query \"{}\" with parameters \"{}\"", ACCOUNT_CAN_ACCESS_LIST_TEMPLATE, parameters, cause);
                promise.fail(cause);
                return;
            }

            var accountCanAccessList = asyncCanAccess.result().getInteger(0) > 0;
            if (accountCanAccessList) {
                log.debug("Account can access list");
                promise.complete();
            } else {
                log.debug("Account cannot access list");
                promise.fail(new ForbiddenException(String.format("Account \"%d\" cannot access list \"%d\"", accountId, listId)));
            }
        });

        return promise.future();
    }

    private Future<Void> validateAccountParticipatesInQuiz(SQLConnection connection, Integer accountId, Integer quizId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(accountId).add(quizId);
        connection.querySingleWithParams(ACCOUNT_PARTICIPATES_IN_QUIZ_TEMPLATE, parameters, asyncCanAccess -> {
            if (asyncCanAccess.failed()) {
                var cause = asyncCanAccess.cause();
                log.error("Unable to execute query \"{}\" with parameters \"{}\"", ACCOUNT_PARTICIPATES_IN_QUIZ_TEMPLATE, parameters, cause);
                promise.fail(cause);
                return;
            }

            var accountParticipatesInQuiz = asyncCanAccess.result().getInteger(0) > 0;
            if (accountParticipatesInQuiz) {
                log.debug("Account participates in quiz");
                promise.complete();
            } else {
                log.debug("Account does not participate in quiz");
                promise.fail(new ForbiddenException(String.format("Account \"%d\" does not participate in quiz with ID \"%d\"", accountId, quizId)));
            }
        });

        return promise.future();
    }
}
