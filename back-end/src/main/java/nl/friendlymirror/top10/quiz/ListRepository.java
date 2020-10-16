package nl.friendlymirror.top10.quiz;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLConnection;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.ForbiddenException;
import nl.friendlymirror.top10.NotFoundException;
import nl.friendlymirror.top10.quiz.dto.*;

@Log4j2
public class ListRepository {

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
    private static final String FINALIZE_LIST_TEMPLATE = "UPDATE list SET has_draft_status = false WHERE list_id = ?";
    private static final String ASSIGN_LIST_TEMPLATE = "INSERT INTO assignment (list_id, account_id, assignee_id) VALUES (?, ?, ?) "
                                                       + "ON CONFLICT (list_id, account_id) DO "
                                                       + "UPDATE SET assignee_id = EXCLUDED.assignee_id";

    public Future<ListsDto> getAllListsForQuiz(SQLConnection connection, String externalId) {
        var promise = Promise.<ListsDto> promise();

        var parameters = new JsonArray().add(externalId);
        connection.queryWithParams(GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, parameters, asyncLists -> {
            if (asyncLists.failed()) {
                handleFailure(promise, GET_ALL_LISTS_FOR_QUIZ_TEMPLATE, parameters, asyncLists);
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
                            .listId(row.getInteger("list_id"))
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
                        .videoId(row.getInteger("video_id"))
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

    public Future<Void> addVideo(SQLConnection connection, Integer listId, String url) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(listId).add(url);
        connection.updateWithParams(ADD_VIDEO_TEMPLATE, parameters, asyncAddVideo -> {
            if (asyncAddVideo.failed()) {
                handleFailure(promise, ADD_VIDEO_TEMPLATE, parameters, asyncAddVideo);
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

    public Future<Void> assignList(SQLConnection connection, Integer accountId, Integer listId, Integer assigneeId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(listId).add(accountId).add(assigneeId);
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

    public Future<Void> validateAccountParticipatesInQuiz(SQLConnection connection, Integer accountId, Integer quizId) {
        var promise = Promise.<Void> promise();

        var parameters = new JsonArray().add(accountId).add(quizId);
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
                promise.fail(new ForbiddenException(String.format("Account \"%d\" does not participate in quiz with ID \"%d\"", accountId, quizId)));
            }
        });

        return promise.future();
    }
}