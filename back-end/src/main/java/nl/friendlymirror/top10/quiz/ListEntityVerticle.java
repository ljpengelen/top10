package nl.friendlymirror.top10.quiz;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.entity.AbstractEntityVerticle;

@Log4j2
@RequiredArgsConstructor
public class ListEntityVerticle extends AbstractEntityVerticle {

    public static final String GET_ALL_LISTS_FOR_QUIZ_ADDRESS = "entity.list.getAllForQuiz";
    public static final String GET_ALL_LISTS_FOR_ACCOUNT_ADDRESS = "entity.list.getAllForAccount";
    public static final String GET_ONE_LIST_ADDRESS = "entity.list.getOne";
    public static final String ADD_VIDEO_ADDRESS = "entity.list.addVideo";
    public static final String FINALIZE_LIST_ADDRESS = "entity.list.finalize";
    public static final String ASSIGN_LIST_ADDRESS = "entity.list.assign";

    private static final String GET_ALL_LISTS_FOR_QUIZ_TEMPLATE = "SELECT v.video_id, v.list_id, v.url, l.has_draft_status FROM video v "
                                                                  + "NATURAL JOIN list l "
                                                                  + "JOIN quiz q ON l.quiz_id = q.quiz_id "
                                                                  + "WHERE q.external_id = ? AND NOT l.has_draft_status";
    private static final String GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE = "SELECT v.video_id, l.list_id, v.url, l.has_draft_status FROM video v "
                                                                     + "NATURAL RIGHT JOIN list l "
                                                                     + "WHERE l.account_id = ?";
    private static final String GET_ONE_LIST_TEMPLATE = "SELECT v.video_id, l.list_id, v.url, l.has_draft_status, a.assignee_id FROM video v "
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
        sqlClient.queryWithParams(GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, new JsonArray().add(externalId), asyncLists -> {
            if (asyncLists.failed()) {
                log.error("Unable to retrieve all lists for external ID \"{}\"", externalId, asyncLists.cause());
                getAllListsRequest.fail(500, "Unable to retrieve all lists");
                return;
            }

            log.debug("Retrieved all lists for quiz");

            getAllListsRequest.reply(listsRowsToJsonArray(asyncLists.result().getResults()));
        });
    }

    private JsonArray listsRowsToJsonArray(List<JsonArray> listsRows) {
        var videoMap = new HashMap<Integer, JsonArray>();
        var hasDraftStatusMap = new HashMap<Integer, Boolean>();
        listsRows.forEach(listRow -> {
            videoMap.put(listRow.getInteger(1), new JsonArray());
            hasDraftStatusMap.put(listRow.getInteger(1), listRow.getBoolean(3));
        });

        listsRows.forEach(listRow -> {
            var videoId = listRow.getInteger(0);
            if (videoId != null) {
                var video = new JsonObject()
                        .put("videoId", videoId)
                        .put("url", listRow.getString(2));
                videoMap.get(listRow.getInteger(1)).add(video);
            }
        });

        var lists = videoMap.entrySet().stream()
                .map(entry -> {
                    var listId = entry.getKey();
                    return new JsonObject()
                            .put("listId", listId)
                            .put("hasDraftStatus", hasDraftStatusMap.get(listId))
                            .put("videos", entry.getValue());
                }).collect(Collectors.toList());

        return new JsonArray(lists);
    }

    private JsonObject listRowsToJsonObject(List<JsonArray> listRows) {
        if (listRows.isEmpty()) {
            return null;
        }

        var firstVideo = listRows.get(0);
        var listId = firstVideo.getInteger(1);
        var hasDraftStatus = firstVideo.getBoolean(3);
        var assigneeId = firstVideo.getInteger(4);

        var videos = listRows.stream()
                .filter(video -> video.getInteger(0) != null)
                .map(video -> new JsonObject()
                        .put("videoId", video.getInteger(0))
                        .put("url", video.getString(2)))
                .collect(Collectors.toList());

        return new JsonObject()
                .put("listId", listId)
                .put("hasDraftStatus", hasDraftStatus)
                .put("videos", new JsonArray(videos))
                .put("assigneeId", assigneeId);
    }

    private void handleGetAllForAccount(Message<Integer> getAllListsForAccountRequest) {
        var accountId = getAllListsForAccountRequest.body();
        sqlClient.queryWithParams(GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE, new JsonArray().add(accountId), asyncLists -> {
            if (asyncLists.failed()) {
                log.error("Unable to retrieve all lists for account ID \"{}\"", accountId, asyncLists.cause());
                getAllListsForAccountRequest.fail(500, "Unable to retrieve all lists for account");
                return;
            }

            log.debug("Retrieved all lists for account");

            getAllListsForAccountRequest.reply(listsRowsToJsonArray(asyncLists.result().getResults()));
        });
    }

    private void handleGetOne(Message<JsonObject> getOneListRequest) {
        var body = getOneListRequest.body();
        var listId = body.getInteger("listId");
        var accountId = body.getInteger("accountId");
        var getListRequest = new JsonArray().add(listId);
        sqlClient.queryWithParams(GET_ONE_LIST_TEMPLATE, getListRequest, asyncList -> {
            if (asyncList.failed()) {
                log.error("Unable to retrieve list with ID \"{}\"", listId, asyncList.cause());
                getOneListRequest.fail(500, "Unable to retrieve list");
                return;
            }

            log.debug("Retrieved list");

            getOneListRequest.reply(listRowsToJsonObject(asyncList.result().getResults()));
        });
    }

    private void handleAddVideo(Message<JsonObject> addVideoRequest) {
        var body = addVideoRequest.body();
        var listId = body.getInteger("listId");
        var url = body.getString("url");
        var accountId = body.getInteger("accountId");

        var addVideoParameters = new JsonArray().add(listId).add(url);
        sqlClient.updateWithParams(ADD_VIDEO_TEMPLATE, addVideoParameters, asyncAddVideo -> {
            if (asyncAddVideo.failed()) {
                log.error("Unable to add video: \"{}\"", addVideoRequest, asyncAddVideo.cause());
                addVideoRequest.fail(500, "Unable to add video");
                return;
            }

            var numberOfAffectedRows = asyncAddVideo.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Added video");
            } else {
                log.debug("Unable to add video");
            }

            addVideoRequest.reply(numberOfAffectedRows > 0);
        });
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

        var assignmentRequest = new JsonArray().add(listId).add(accountId).add(assigneeId);
        sqlClient.updateWithParams(ASSIGN_LIST_TEMPLATE, assignmentRequest, asyncAssignment -> {
            if (asyncAssignment.failed()) {
                log.error("Unable to assign list \"{}\" to account \"{}\"", listId, assigneeId, asyncAssignment.cause());
                assignListRequest.fail(500, "Unable to assign list to account");
                return;
            }

            var numberOfAffectedRows = asyncAssignment.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Assigned list");
            } else {
                log.debug("Unable to assign list");
            }

            assignListRequest.reply(null);
        });
    }
}
