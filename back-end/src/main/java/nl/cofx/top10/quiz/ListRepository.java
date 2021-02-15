package nl.cofx.top10.quiz;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLConnection;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.*;
import nl.cofx.top10.quiz.dto.*;

@Log4j2
public class ListRepository {

    private static final String GET_ALL_LISTS_FOR_QUIZ_TEMPLATE = "SELECT l.list_id FROM list l "
                                                                  + "JOIN quiz q ON l.quiz_id = q.quiz_id "
                                                                  + "WHERE q.external_id = ? AND NOT l.has_draft_status";
    private static final String GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE = "SELECT l.list_id FROM list l WHERE l.account_id = ?";
    private static final String GET_VIDEOS_FOR_LISTS_TEMPLATE = "SELECT v.video_id, v.list_id, v.url FROM video v WHERE v.list_id = ANY (?)";
    private static final String ACCOUNT_CAN_ACCESS_LIST_TEMPLATE = "SELECT COUNT(l1.quiz_id) from list l1 "
                                                                   + "JOIN list l2 ON l1.quiz_id = l2.quiz_id "
                                                                   + "JOIN quiz q ON l1.quiz_id = q.quiz_id "
                                                                   + "WHERE l1.account_id = ? AND l2.list_id = ? "
                                                                   + "AND (q.deadline <= NOW() OR l1.list_id = l2.list_id)";
    private static final String ACCOUNT_PARTICIPATES_IN_QUIZ_TEMPLATE = "SELECT COUNT(l.account_id) FROM list l "
                                                                        + "JOIN account a ON l.account_id = a.account_id "
                                                                        + "WHERE a.external_id = ? AND l.quiz_id = ?";
    private static final String GET_ONE_LIST_TEMPLATE =
            "SELECT l.list_id, l.has_draft_status, l.quiz_id, q.external_id AS external_quiz_id, q.is_active, l.account_id AS creator_id, a.name AS creator_name FROM video v "
            + "NATURAL RIGHT JOIN list l "
            + "NATURAL JOIN quiz q "
            + "JOIN account a ON a.account_id = l.account_id "
            + "WHERE l.list_id = ?";
    private static final String GET_ASSIGNMENTS_TEMPLATE = "SELECT l.list_id, acc.external_id, acc.name FROM list l "
                                                           + "LEFT JOIN assignment ass ON l.list_id = ass.list_id "
                                                           + "LEFT JOIN account acc ON ass.assignee_id = acc.account_id "
                                                           + "WHERE l.list_id = ANY (?) and ass.account_id = ?";
    private static final String GET_LIST_BY_VIDEO_ID_TEMPLATE =
            "SELECT l.list_id, l.has_draft_status, l.quiz_id, q.external_id AS external_quiz_id, l.account_id FROM video v "
            + "NATURAL RIGHT JOIN list l "
            + "NATURAL JOIN quiz q "
            + "WHERE v.video_id = ?";
    private static final String ADD_VIDEO_TEMPLATE = "INSERT INTO video (list_id, url) VALUES (?, ?) ON CONFLICT DO NOTHING";
    private static final String DELETE_VIDEO_TEMPLATE = "DELETE FROM video WHERE video_id = ?";
    private static final String FINALIZE_LIST_TEMPLATE = "UPDATE list SET has_draft_status = false WHERE list_id = ?";
    private static final String ASSIGN_LIST_TEMPLATE = "INSERT INTO assignment (list_id, account_id, assignee_id) "
                                                       + "VALUES (?, ?, (SELECT account_id FROM account WHERE external_id = ?)) "
                                                       + "ON CONFLICT (list_id, account_id) DO "
                                                       + "UPDATE SET assignee_id = EXCLUDED.assignee_id";

    public Future<List<ListDto>> getAllListsForQuiz(SQLConnection connection, String externalId) {
        var promise = Promise.<List<ListDto>> promise();

        var parameters = new JsonArray().add(externalId);
        connection.queryWithParams(GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, parameters, asyncLists -> {
            if (asyncLists.failed()) {
                handleFailure(promise, GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, parameters, asyncLists);
                return;
            }

            log.debug("Retrieved all lists for quiz with external ID \"{}\"", externalId);

            var listDtos = asyncLists.result().getRows().stream()
                    .map(row -> ListDto.builder()
                            .id(row.getInteger("list_id"))
                            .build())
                    .collect(Collectors.toList());

            promise.complete(listDtos);
        });

        return promise.future();
    }

    private <T1, T2> void handleFailure(Promise<T1> promise, String template, JsonArray parameters, AsyncResult<T2> asyncResult) {
        var cause = asyncResult.cause();
        log.error("Unable to execute query \"{}\" with parameters \"{}\"", template, parameters, cause);
        promise.fail(cause);
    }

    public Future<List<ListDto>> getAllListsForAccount(SQLConnection connection, Integer accountId) {
        var promise = Promise.<List<ListDto>> promise();

        var parameters = new JsonArray().add(accountId);
        connection.queryWithParams(GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE, parameters, asyncLists -> {
            if (asyncLists.failed()) {
                handleFailure(promise, GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE, parameters, asyncLists);
                return;
            }

            log.debug("Retrieved all lists for account");

            var listDtos = asyncLists.result().getRows().stream()
                    .map(row -> ListDto.builder()
                            .id(row.getInteger("list_id"))
                            .build())
                    .collect(Collectors.toList());

            promise.complete(listDtos);
        });

        return promise.future();
    }

    public Future<Map<Integer, List<VideoDto>>> getVideosForLists(SQLConnection connection, int... listIds) {
        var promise = Promise.<Map<Integer, List<VideoDto>>> promise();

        var parameters = new JsonArray().add(listIds);
        connection.queryWithParams(GET_VIDEOS_FOR_LISTS_TEMPLATE, parameters, asyncVideos -> {
            if (asyncVideos.failed()) {
                handleFailure(promise, GET_VIDEOS_FOR_LISTS_TEMPLATE, parameters, asyncVideos);
                return;
            }

            log.debug("Retrieved all videos for lists");

            Map<Integer, List<VideoDto>> videosForLists = IntStream.of(listIds).boxed()
                    .collect(Collectors.toMap(Function.identity(), i -> new ArrayList<>()));

            asyncVideos.result().getRows().forEach(row -> {
                var listId = row.getInteger("list_id");
                var videoDto = VideoDto.builder()
                        .id(row.getInteger("video_id"))
                        .url(row.getString("url"))
                        .build();
                videosForLists.get(listId).add(videoDto);
            });

            promise.complete(videosForLists);
        });

        return promise.future();
    }

    public Future<ListDto> getList(SQLConnection connection, Integer listId) {
        var promise = Promise.<ListDto> promise();

        var parameters = new JsonArray().add(listId);
        connection.querySingleWithParams(GET_ONE_LIST_TEMPLATE, parameters, asyncList -> {
            if (asyncList.failed()) {
                handleFailure(promise, GET_ONE_LIST_TEMPLATE, parameters, asyncList);
                return;
            }

            log.debug("Retrieved list");

            var row = asyncList.result();
            if (row == null) {
                log.debug("List \"{}\" not found", listId);
                promise.fail(new NotFoundException(String.format("List \"%d\" not found", listId)));
            } else {
                var listDto = ListDto.builder()
                        .id(row.getInteger(0))
                        .hasDraftStatus(row.getBoolean(1))
                        .quizId(row.getInteger(2))
                        .externalQuizId(row.getString(3))
                        .isActiveQuiz(row.getBoolean(4))
                        .creatorId(row.getInteger(5))
                        .creatorName(row.getString(6))
                        .build();
                log.debug("Retrieved list by ID \"{}\": \"{}\"", listId, listDto);
                promise.complete(listDto);
            }
        });

        return promise.future();
    }

    public Future<Map<Integer, AssignmentDto>> getAssignments(SQLConnection connection, Integer accountId, int... listIds) {
        var promise = Promise.<Map<Integer, AssignmentDto>> promise();

        var parameters = new JsonArray().add(listIds).add(accountId);
        connection.queryWithParams(GET_ASSIGNMENTS_TEMPLATE, parameters, asyncAssignments -> {
            if (asyncAssignments.failed()) {
                handleFailure(promise, GET_ASSIGNMENTS_TEMPLATE, parameters, asyncAssignments);
                return;
            }

            log.debug("Retrieved assignments");

            var assignmentsForLists = new HashMap<Integer, AssignmentDto>();

            asyncAssignments.result().getRows().forEach(row -> {
                var listId = row.getInteger("list_id");
                var assignmentDto = AssignmentDto.builder()
                        .externalAssigneeId(row.getString("external_id"))
                        .assigneeName(row.getString("name"))
                        .build();
                assignmentsForLists.put(listId, assignmentDto);
            });

            promise.complete(assignmentsForLists);
        });

        return promise.future();
    }

    public Future<ListDto> getListByVideoId(SQLConnection connection, Integer videoId) {
        var promise = Promise.<ListDto> promise();

        var parameters = new JsonArray().add(videoId);
        connection.querySingleWithParams(GET_LIST_BY_VIDEO_ID_TEMPLATE, parameters, asyncList -> {
            if (asyncList.failed()) {
                handleFailure(promise, GET_LIST_BY_VIDEO_ID_TEMPLATE, parameters, asyncList);
                return;
            }

            log.debug("Retrieved list by video ID");

            var row = asyncList.result();
            if (row == null) {
                log.debug("List for video ID \"{}\" not found", videoId);
                promise.fail(new NotFoundException(String.format("List for video ID \"%d\" not found", videoId)));
            } else {
                var listDto = ListDto.builder()
                        .id(row.getInteger(0))
                        .hasDraftStatus(row.getBoolean(1))
                        .quizId(row.getInteger(2))
                        .externalQuizId(row.getString(3))
                        .creatorId(row.getInteger(4))
                        .build();
                log.debug("Retrieved list by video ID \"{}\": \"{}\"", videoId, listDto);
                promise.complete(listDto);
            }
        });

        return promise.future();
    }

    public Future<Integer> addVideo(SQLConnection connection, Integer listId, String url) {
        var promise = Promise.<Integer> promise();

        var parameters = new JsonArray().add(listId).add(url);
        connection.updateWithParams(ADD_VIDEO_TEMPLATE, parameters, asyncAddVideo -> {
            if (asyncAddVideo.failed()) {
                handleFailure(promise, ADD_VIDEO_TEMPLATE, parameters, asyncAddVideo);
                return;
            }

            var numberOfAffectedRows = asyncAddVideo.result().getUpdated();
            if (numberOfAffectedRows == 1) {
                log.debug("Added video");
                promise.complete(asyncAddVideo.result().getKeys().getInteger(0));
            } else {
                log.debug("Unable to add video");
                promise.fail(new InternalServerErrorException(String.format("Updated \"%d\" rows when adding video", numberOfAffectedRows)));
            }
        });

        return promise.future();
    }

    public Future<Void> deleteVideo(SQLConnection connection, Integer videoId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(videoId);
        connection.updateWithParams(DELETE_VIDEO_TEMPLATE, parameters, asyncDeleteVideo -> {
            if (asyncDeleteVideo.failed()) {
                handleFailure(promise, DELETE_VIDEO_TEMPLATE, parameters, asyncDeleteVideo);
                return;
            }

            var numberOfAffectedRows = asyncDeleteVideo.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Deleted video");
            } else {
                log.debug("Unable to delete video");
            }

            promise.complete();
        });

        return promise.future();
    }

    public Future<Void> finalizeList(SQLConnection connection, Integer listId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(listId);
        connection.updateWithParams(FINALIZE_LIST_TEMPLATE, parameters, asyncFinalize -> {
            if (asyncFinalize.failed()) {
                handleFailure(promise, FINALIZE_LIST_TEMPLATE, parameters, asyncFinalize);
                return;
            }

            var numberOfAffectedRows = asyncFinalize.result().getUpdated();
            if (numberOfAffectedRows > 0) {
                log.debug("Finalized list");
            } else {
                log.debug("Unable to finalize list");
            }

            promise.complete();
        });

        return promise.future();
    }

    public Future<Void> assignList(SQLConnection connection, Integer accountId, Integer listId, String externalAssigneeId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(listId).add(accountId).add(externalAssigneeId);
        connection.updateWithParams(ASSIGN_LIST_TEMPLATE, parameters, asyncAssignment -> {
            if (asyncAssignment.failed()) {
                handleFailure(promise, ASSIGN_LIST_TEMPLATE, parameters, asyncAssignment);
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

    public Future<Void> validateAccountCanAccessList(SQLConnection connection, Integer accountId, Integer listId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(accountId).add(listId);
        connection.querySingleWithParams(ACCOUNT_CAN_ACCESS_LIST_TEMPLATE, parameters, asyncCanAccess -> {
            if (asyncCanAccess.failed()) {
                handleFailure(promise, ACCOUNT_CAN_ACCESS_LIST_TEMPLATE, parameters, asyncCanAccess);
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

    public Future<Void> validateAccountParticipatesInQuiz(SQLConnection connection, String externalAccountId, Integer quizId, String externalQuizId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(externalAccountId).add(quizId);
        connection.querySingleWithParams(ACCOUNT_PARTICIPATES_IN_QUIZ_TEMPLATE, parameters, asyncParticipatesInQuiz -> {
            if (asyncParticipatesInQuiz.failed()) {
                handleFailure(promise, ACCOUNT_PARTICIPATES_IN_QUIZ_TEMPLATE, parameters, asyncParticipatesInQuiz);
                return;
            }

            var accountParticipatesInQuiz = asyncParticipatesInQuiz.result().getInteger(0) > 0;
            if (accountParticipatesInQuiz) {
                log.debug("Account participates in quiz");
                promise.complete();
            } else {
                log.debug("Account does not participate in quiz");
                var message = String.format("Account with external ID \"%s\" does not participate in quiz with external ID \"%s\"", externalAccountId, externalQuizId);
                promise.fail(new ForbiddenException(message));
            }
        });

        return promise.future();
    }
}
