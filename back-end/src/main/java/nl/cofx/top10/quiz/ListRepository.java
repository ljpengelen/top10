package nl.cofx.top10.quiz;

import static nl.cofx.top10.postgresql.PostgreSql.toUuid;
import static nl.cofx.top10.postgresql.PostgreSql.toUuids;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLConnection;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.*;
import nl.cofx.top10.quiz.dto.*;

@Log4j2
public class ListRepository {

    private static final String GET_ALL_LISTS_FOR_QUIZ_TEMPLATE = "SELECT replace(l.list_id::text, '-', '') AS list_id FROM list l "
                                                                  + "JOIN quiz q ON l.quiz_id = q.quiz_id "
                                                                  + "WHERE q.quiz_id = ? AND NOT l.has_draft_status "
                                                                  + "ORDER BY list_id";
    private static final String GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE = "SELECT replace(l.list_id::text, '-', '') AS list_id FROM list l WHERE l.account_id = ?";
    private static final String GET_VIDEOS_FOR_LISTS_TEMPLATE = "SELECT v.video_id, replace(v.list_id::text, '-', '') AS list_id, v.url, v.reference_id FROM video v WHERE v.list_id = ANY (?)";
    private static final String ACCOUNT_CAN_ACCESS_LIST_TEMPLATE = "SELECT COUNT(l1.quiz_id) from list l1 "
                                                                   + "JOIN list l2 ON l1.quiz_id = l2.quiz_id "
                                                                   + "JOIN quiz q ON l1.quiz_id = q.quiz_id "
                                                                   + "WHERE l1.account_id = ? AND l2.list_id = ? "
                                                                   + "AND (q.deadline <= NOW() OR l1.list_id = l2.list_id)";
    private static final String ACCOUNT_PARTICIPATES_IN_QUIZ_TEMPLATE = "SELECT COUNT(l.account_id) FROM list l "
                                                                        + "JOIN account a ON l.account_id = a.account_id "
                                                                        + "WHERE a.account_id = ? AND l.quiz_id = ?";
    private static final String GET_ONE_LIST_TEMPLATE =
            "SELECT replace(l.list_id::text, '-', '') AS list_id, l.has_draft_status, replace(l.quiz_id::text, '-', '') AS quiz_id, q.is_active, replace(l.account_id::text, '-', '') AS creator_id, a.name AS creator_name FROM video v "
            + "NATURAL RIGHT JOIN list l "
            + "NATURAL JOIN quiz q "
            + "JOIN account a ON a.account_id = l.account_id "
            + "WHERE l.list_id = ?";
    private static final String GET_ASSIGNMENTS_TEMPLATE =
            "SELECT replace(l.list_id::text, '-', '') AS list_id, replace(acc.account_id::text, '-', '') AS account_id, acc.name FROM list l "
            + "LEFT JOIN assignment ass ON l.list_id = ass.list_id "
            + "LEFT JOIN account acc ON ass.assignee_id = acc.account_id "
            + "WHERE l.list_id = ANY (?) and ass.account_id = ?";
    private static final String GET_LIST_BY_VIDEO_ID_TEMPLATE =
            "SELECT replace(l.list_id::text, '-', '') AS list_id, l.has_draft_status, replace(l.quiz_id::text, '-', '') AS quiz_id, replace(l.account_id::text, '-', '') AS account_id FROM video v "
            + "NATURAL RIGHT JOIN list l "
            + "NATURAL JOIN quiz q "
            + "WHERE v.video_id = ?";
    private static final String ADD_VIDEO_TEMPLATE = "INSERT INTO video (list_id, url, reference_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
    private static final String DELETE_VIDEO_TEMPLATE = "DELETE FROM video WHERE video_id = ?";
    private static final String FINALIZE_LIST_TEMPLATE = "UPDATE list SET has_draft_status = false WHERE list_id = ?";
    private static final String ASSIGN_LIST_TEMPLATE = "INSERT INTO assignment (list_id, account_id, assignee_id) "
                                                       + "VALUES (?, ?, ?) "
                                                       + "ON CONFLICT (list_id, account_id) DO "
                                                       + "UPDATE SET assignee_id = EXCLUDED.assignee_id";

    public Future<List<ListDto>> getAllListsForQuiz(SQLConnection connection, String quizId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(quizId));
            connection.queryWithParams(GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, parameters, asyncLists -> {
                if (asyncLists.failed()) {
                    handleFailure(promise, GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, parameters, asyncLists);
                    return;
                }

                log.debug("Retrieved all lists for quiz \"{}\"", quizId);

                var listDtos = asyncLists.result().getRows().stream()
                        .map(row -> ListDto.builder()
                                .id(row.getString("list_id"))
                                .build())
                        .collect(Collectors.toList());

                promise.complete(listDtos);
            });
        });
    }

    private <T1, T2> void handleFailure(Promise<T1> promise, String template, JsonArray parameters, AsyncResult<T2> asyncResult) {
        var cause = asyncResult.cause();
        log.error("Unable to execute query \"{}\" with parameters \"{}\"", template, parameters, cause);
        promise.fail(cause);
    }

    public Future<List<ListDto>> getAllListsForAccount(SQLConnection connection, String accountId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(accountId));
            connection.queryWithParams(GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE, parameters, asyncLists -> {
                if (asyncLists.failed()) {
                    handleFailure(promise, GET_ALL_LISTS_FOR_ACCOUNT_TEMPLATE, parameters, asyncLists);
                    return;
                }

                log.debug("Retrieved all lists for account");

                var listDtos = asyncLists.result().getRows().stream()
                        .map(row -> ListDto.builder()
                                .id(row.getString("list_id"))
                                .build())
                        .collect(Collectors.toList());

                promise.complete(listDtos);
            });
        });
    }

    @SneakyThrows
    public Future<Map<String, List<VideoDto>>> getVideosForLists(SQLConnection connection, List<String> listIds) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuids(listIds));
            connection.queryWithParams(GET_VIDEOS_FOR_LISTS_TEMPLATE, parameters, asyncVideos -> {
                if (asyncVideos.failed()) {
                    handleFailure(promise, GET_VIDEOS_FOR_LISTS_TEMPLATE, parameters, asyncVideos);
                    return;
                }

                log.debug("Retrieved all videos for lists");

                Map<String, List<VideoDto>> videosForLists = listIds.stream()
                        .collect(Collectors.toMap(Function.identity(), i -> new ArrayList<>()));

                asyncVideos.result().getRows().forEach(row -> {
                    var listId = row.getString("list_id");
                    var videoDto = VideoDto.builder()
                            .id(row.getString("video_id"))
                            .url(row.getString("url"))
                            .referenceId(row.getString("reference_id"))
                            .build();
                    videosForLists.get(listId).add(videoDto);
                });

                promise.complete(videosForLists);
            });
        });
    }

    public Future<ListDto> getList(SQLConnection connection, String listId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(listId));
            connection.querySingleWithParams(GET_ONE_LIST_TEMPLATE, parameters, asyncList -> {
                if (asyncList.failed()) {
                    handleFailure(promise, GET_ONE_LIST_TEMPLATE, parameters, asyncList);
                    return;
                }

                log.debug("Retrieved list");

                var row = asyncList.result();
                if (row == null) {
                    log.debug("List \"{}\" not found", listId);
                    promise.fail(new NotFoundException(String.format("List \"%s\" not found", listId)));
                } else {
                    var listDto = ListDto.builder()
                            .id(row.getString(0))
                            .hasDraftStatus(row.getBoolean(1))
                            .quizId(row.getString(2))
                            .isActiveQuiz(row.getBoolean(3))
                            .creatorId(row.getString(4))
                            .creatorName(row.getString(5))
                            .build();
                    log.debug("Retrieved list by ID \"{}\": \"{}\"", listId, listDto);
                    promise.complete(listDto);
                }
            });
        });
    }

    public Future<Map<String, AssignmentDto>> getAssignments(SQLConnection connection, String accountId, List<String> listIds) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuids(listIds)).add(toUuid(accountId));
            connection.queryWithParams(GET_ASSIGNMENTS_TEMPLATE, parameters, asyncAssignments -> {
                if (asyncAssignments.failed()) {
                    handleFailure(promise, GET_ASSIGNMENTS_TEMPLATE, parameters, asyncAssignments);
                    return;
                }

                log.debug("Retrieved assignments");

                var assignmentsForLists = new HashMap<String, AssignmentDto>();

                asyncAssignments.result().getRows().forEach(row -> {
                    var listId = row.getString("list_id");
                    var assignmentDto = AssignmentDto.builder()
                            .assigneeId(row.getString("account_id"))
                            .assigneeName(row.getString("name"))
                            .build();
                    assignmentsForLists.put(listId, assignmentDto);
                });

                promise.complete(assignmentsForLists);
            });
        });
    }

    public Future<ListDto> getListByVideoId(SQLConnection connection, String videoId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(videoId));
            connection.querySingleWithParams(GET_LIST_BY_VIDEO_ID_TEMPLATE, parameters, asyncList -> {
                if (asyncList.failed()) {
                    handleFailure(promise, GET_LIST_BY_VIDEO_ID_TEMPLATE, parameters, asyncList);
                    return;
                }

                log.debug("Retrieved list by video ID");

                var row = asyncList.result();
                if (row == null) {
                    log.debug("List for video \"{}\" not found", videoId);
                    promise.fail(new NotFoundException(String.format("List for video \"%s\" not found", videoId)));
                } else {
                    var listDto = ListDto.builder()
                            .id(row.getString(0))
                            .hasDraftStatus(row.getBoolean(1))
                            .quizId(row.getString(2))
                            .creatorId(row.getString(3))
                            .build();
                    log.debug("Retrieved list by video \"{}\": \"{}\"", videoId, listDto);
                    promise.complete(listDto);
                }
            });
        });
    }

    public Future<String> addVideo(SQLConnection connection, String listId, String url, String referenceId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(listId)).add(url).add(referenceId);
            connection.updateWithParams(ADD_VIDEO_TEMPLATE, parameters, asyncAddVideo -> {
                if (asyncAddVideo.failed()) {
                    handleFailure(promise, ADD_VIDEO_TEMPLATE, parameters, asyncAddVideo);
                    return;
                }

                var numberOfAffectedRows = asyncAddVideo.result().getUpdated();
                if (numberOfAffectedRows == 1) {
                    log.debug("Added video");
                    promise.complete(asyncAddVideo.result().getKeys().getString(2));
                } else {
                    log.debug("Unable to add video");
                    promise.fail(new InternalServerErrorException(String.format("Updated \"%d\" rows when adding video", numberOfAffectedRows)));
                }
            });
        });
    }

    public Future<Void> deleteVideo(SQLConnection connection, String videoId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(videoId));
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
        });
    }

    public Future<Void> finalizeList(SQLConnection connection, String listId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(listId));
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
        });
    }

    public Future<Void> assignList(SQLConnection connection, String accountId, String listId, String assigneeId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(listId)).add(toUuid(accountId)).add(toUuid(assigneeId));
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
        });
    }

    public Future<Void> validateAccountCanAccessList(SQLConnection connection, String accountId, String listId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(accountId)).add(toUuid(listId));
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
                    promise.fail(new ForbiddenException(String.format("Account \"%s\" cannot access list \"%s\"", accountId, listId)));
                }
            });
        });
    }

    public Future<Void> validateAccountParticipatesInQuiz(SQLConnection connection, String accountId, String quizId) {
        return Future.future(promise -> {
            var parameters = new JsonArray().add(toUuid(accountId)).add(toUuid(quizId));
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
                    var message = String.format("Account \"%s\" does not participate in quiz \"%s\"", accountId, quizId);
                    promise.fail(new ForbiddenException(message));
                }
            });
        });
    }
}
