package nl.friendlymirror.top10.quiz;

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
    private static final String GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE = "SELECT v.video_id, v.list_id, v.url, l.has_draft_status FROM video v "
                                                                     + "NATURAL JOIN list l "
                                                                     + "WHERE l.account_id = ?";
    private static final String GET_ONE_LIST_TEMPLATE = "SELECT v.video_id, v.list_id, v.url, l.has_draft_status FROM video v "
                                                        + "NATURAL JOIN list l "
                                                        + "WHERE l.list_id = ? AND EXISTS "
                                                        + "(SELECT list_id FROM list l "
                                                        + "JOIN participant p ON l.quiz_id = p.quiz_id"
                                                        + "WHERE list_id = ? AND p.account_id = ?)";
    private static final String ADD_VIDEO_TEMPLATE = "INSERT INTO video SET (list_id, url) VALUES (?, ?) "
                                                     + "WHERE EXISTS (SELECT list_id FROM list WHERE account_id = ? AND has_draft_status)";
    private static final String FINALIZE_LIST_TEMPLATE = "UPDATE list SET has_draft_status = false WHERE list_id = ? and account_id = ?";
    private static final String ASSIGN_LIST_TEMPLATE = "INSERT INTO assignment (list_id, account_id, assignee_id) VALUES (?, ?, ?) "
                                                       + "ON CONFLICT DO "
                                                       + "UPDATE SET (assignee_id) = (EXCLUDED.assignee_id)";

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

            var lists = asyncLists.result().getResults().stream()
                    .map(this::listArrayToJsonObject)
                    .collect(Collectors.toList());

            getAllListsRequest.reply(new JsonArray(lists));
        });
    }

    private JsonObject listArrayToJsonObject(JsonArray array) {
        return new JsonObject()
                .put("videoId", array.getInteger(0))
                .put("listId", array.getInteger(1))
                .put("url", array.getString(2))
                .put("hasDraftStatus", array.getBoolean(3));
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

            var lists = asyncLists.result().getResults().stream()
                    .map(this::listArrayToJsonObject)
                    .collect(Collectors.toList());

            getAllListsForAccountRequest.reply(new JsonArray(lists));
        });
    }

    private void handleGetOne(Message<JsonObject> getOneListRequest) {
        var body = getOneListRequest.body();
        var listId = body.getInteger("listId");
        var accountId = body.getInteger("accountId");
        var getListRequest = new JsonArray().add(listId).add(listId).add(accountId);
        sqlClient.querySingleWithParams(GET_ONE_LIST_TEMPLATE, getListRequest, asyncList -> {
            if (asyncList.failed()) {
                log.error("Unable to retrieve list with ID \"{}\"", listId, asyncList.cause());
                getOneListRequest.fail(500, "Unable to retrieve list");
                return;
            }

            log.debug("Retrieved list");

            getOneListRequest.reply(listArrayToJsonObject(asyncList.result()));
        });
    }

    private void handleAddVideo(Message<JsonObject> addVideoRequest) {
        var body = addVideoRequest.body();
        var listId = body.getInteger("listId");
        var url = body.getString("url");
        var accountId = body.getInteger("accountId");

        var addVideoParameters = new JsonArray().add(listId).add(url).add(accountId);
        sqlClient.updateWithParams(ADD_VIDEO_TEMPLATE, addVideoParameters, asyncAddVideo -> {
            if (asyncAddVideo.failed()) {
                log.error("Unable to add video: \"{}\"", addVideoRequest, asyncAddVideo.cause());
                addVideoRequest.fail(500, "Unable to add video");
                return;
            }

            log.debug("Added video");

            addVideoRequest.reply(null);
        });
    }

    private void handleFinalizeList(Message<JsonObject> finalizeListRequest) {
        var body = finalizeListRequest.body();
        var accountId = body.getInteger("accountId");
        var listId = body.getString("listId");

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
                log.debug("Account is not authorized to finalize list");
            }

            finalizeListRequest.reply(numberOfAffectedRows > 0);
        });
    }

    private void handleAssignList(Message<JsonObject> assignListRequest) {
        var body = assignListRequest.body();
        var accountId = body.getInteger("accountId");
        var listId = body.getString("listId");
        var assigneeId = body.getString("assigneeId");

        var assignmentRequest = new JsonArray().add(listId).add(accountId).add(assigneeId);
        sqlClient.updateWithParams(ASSIGN_LIST_TEMPLATE, assignmentRequest, asyncAssignment -> {
            if (asyncAssignment.failed()) {
                log.error("Unable to assign list \"{}\" to account \"{}\"", listId, assigneeId, asyncAssignment.cause());
                assignListRequest.fail(500, "Unable to assign list to account");
                return;
            }

            log.debug("Assigned list");

            assignListRequest.reply(null);
        });
    }
}
