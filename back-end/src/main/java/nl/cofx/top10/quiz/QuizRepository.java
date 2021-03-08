package nl.cofx.top10.quiz;

import static nl.cofx.top10.postgresql.PostgreSql.toUuid;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.ConflictException;
import nl.cofx.top10.NotFoundException;
import nl.cofx.top10.quiz.dto.*;

@Log4j2
public class QuizRepository {

    private static final String GET_ALL_QUIZZES_TEMPLATE =
            "SELECT replace(q.quiz_id::text, '-', '') AS quiz_id, q.name, q.is_active, replace(q.creator_id::text, '-', '') AS creator_id, q.deadline, replace(l.list_id::text, '-', '') AS list_id, l.has_draft_status FROM quiz q "
            + "JOIN list l ON l.quiz_id = q.quiz_id "
            + "WHERE l.account_id = ?";
    private static final String GET_ONE_QUIZ_TEMPLATE =
            "SELECT replace(q.quiz_id::text, '-', '') AS quiz_id, q.name, q.is_active, replace(q.creator_id::text, '-', '') AS creator_id, q.deadline, replace(l.list_id::text, '-', '') AS list_id, l.has_draft_status FROM quiz q "
            + "LEFT JOIN list l ON l.quiz_id = q.quiz_id AND l.account_id = ? "
            + "WHERE q.quiz_id = ?";
    private static final String CREATE_QUIZ_TEMPLATE = "INSERT INTO quiz (name, is_active, creator_id, deadline) VALUES (?, true, ?, ?)";
    private static final String COMPLETE_QUIZ_TEMPLATE = "UPDATE quiz SET is_active = false WHERE creator_id = ? AND quiz_id = ?";
    private static final String CREATE_LIST_TEMPLATE = "INSERT INTO list (account_id, quiz_id, has_draft_status) "
                                                       + "VALUES (?, ?, true) "
                                                       + "ON CONFLICT DO NOTHING";
    private static final String GET_PARTICIPANTS_TEMPLATE =
            "SELECT replace(acc.account_id::text, '-', '') AS account_id, acc.name, l.has_draft_status, replace(ass.list_id::text, '-', '') FROM account acc "
            + "JOIN list l ON l.account_id = acc.account_id "
            + "JOIN quiz q ON l.quiz_id = q.quiz_id "
            + "LEFT JOIN assignment ass ON (ass.assignee_id = acc.account_id AND ass.account_id = ?) "
            + "WHERE q.quiz_id = ? "
            + "ORDER BY acc.account_id";
    private static final String GET_QUIZ_RESULT_TEMPLATE =
            "SELECT replace(ass.list_id::text, '-', '') AS list_id, "
            + "replace(assigner_acc.account_id::text, '-', '') AS assigner_id, assigner_acc.name AS assigner_name, "
            + "replace(assignee_acc.account_id::text, '-', '') AS assignee_id, assignee_acc.name AS assignee_name, "
            + "replace(creator_acc.account_id::text, '-', '') AS creator_id, creator_acc.name AS creator_name FROM quiz q "
            + "JOIN list l ON l.quiz_id = q.quiz_id "
            + "JOIN assignment ass ON ass.list_id = l.list_id "
            + "JOIN account creator_acc ON creator_acc.account_id = l.account_id "
            + "JOIN account assigner_acc ON assigner_acc.account_id = ass.account_id "
            + "JOIN account assignee_acc ON assignee_acc.account_id = ass.assignee_id "
            + "WHERE q.quiz_id = ?";

    public Future<QuizzesDto> getAllQuizzes(SQLConnection connection, String accountId) {
        return Future.future(promise ->
                connection.queryWithParams(GET_ALL_QUIZZES_TEMPLATE, new JsonArray().add(toUuid(accountId)), asyncQuizzes -> {
                    if (asyncQuizzes.failed()) {
                        var cause = asyncQuizzes.cause();
                        log.error("Unable to retrieve all quizzes for account ID \"{}\"", accountId, cause);
                        promise.fail(cause);
                        return;
                    }

                    log.debug("Retrieved all quizzes for account");

                    var quizzes = asyncQuizzes.result().getResults().stream()
                            .map(quiz -> toQuizDto(quiz, accountId))
                            .collect(Collectors.toList());

                    promise.complete(QuizzesDto.builder()
                            .quizzes(quizzes)
                            .build());
                }));
    }

    private QuizDto toQuizDto(JsonArray array, String accountId) {
        var creatorId = array.getString(3);
        var quizDtoBuilder = QuizDto.builder()
                .id(array.getString(0))
                .name(array.getString(1))
                .isActive(array.getBoolean(2))
                .creatorId(creatorId)
                .isCreator(creatorId.equals(accountId))
                .deadline(array.getInstant(4));

        var personalListId = array.getString(5);
        if (personalListId != null) {
            quizDtoBuilder.personalListId(personalListId);
        }

        var personalListHasDraftStatus = array.getBoolean(6);
        if (personalListHasDraftStatus != null) {
            quizDtoBuilder.personalListHasDraftStatus(personalListHasDraftStatus);
        }

        return quizDtoBuilder.build();
    }

    public Future<QuizDto> getQuiz(SQLConnection connection, String quizId, String accountId) {
        return Future.future(promise ->
                connection.querySingleWithParams(GET_ONE_QUIZ_TEMPLATE, new JsonArray().add(toUuid(accountId)).add(toUuid(quizId)), asyncQuiz -> {
                    if (asyncQuiz.failed()) {
                        var cause = asyncQuiz.cause();
                        log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_ONE_QUIZ_TEMPLATE, quizId, cause);
                        promise.fail(cause);
                        return;
                    }

                    if (asyncQuiz.result() == null) {
                        log.debug("Quiz \"{}\" not found", quizId);
                        promise.fail(new NotFoundException(String.format("Quiz \"%s\" not found", quizId)));
                    } else {
                        var quiz = toQuizDto(asyncQuiz.result(), accountId);
                        log.debug("Retrieved quiz \"{}\": \"{}\"", quizId, quiz);
                        promise.complete(quiz);
                    }
                }));
    }

    public Future<ResultSummaryDto> getQuizResult(SQLConnection connection, String quizId) {
        return Future.future(promise ->
                connection.queryWithParams(GET_QUIZ_RESULT_TEMPLATE, new JsonArray().add(toUuid(quizId)), asyncQuizResult -> {
                    if (asyncQuizResult.failed()) {
                        var cause = asyncQuizResult.cause();
                        log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_QUIZ_RESULT_TEMPLATE, quizId, cause);
                        promise.fail(cause);
                        return;
                    }

                    var rows = asyncQuizResult.result().getRows();
                    var quizResult = assignmentsToQuizResult(quizId, rows);
                    log.debug("Retrieved result for quiz \"{}\": \"{}\"", quizId, quizResult);
                    promise.complete(quizResult);
                }));
    }

    private ResultSummaryDto assignmentsToQuizResult(String quizId, List<JsonObject> assignments) {
        return ResultSummaryDto.builder()
                .quizId(quizId)
                .personalResults(toPersonalResults(assignments))
                .build();
    }

    private Map<String, PersonalResultDto> toPersonalResults(List<JsonObject> assignments) {
        var personalResults = new HashMap<String, PersonalResultDto.PersonalResultDtoBuilder>();

        assignments.forEach(assignment -> {
            var accountId = assignment.getString("assigner_id");
            var name = assignment.getString("assigner_name");
            var personalResult = personalResults.computeIfAbsent(accountId, key ->
                    PersonalResultDto.builder()
                            .accountId(key)
                            .name(name));
            var assigneeId = assignment.getString("assignee_id");
            var assigneeName = assignment.getString("assignee_name");
            var creatorId = assignment.getString("creator_id");
            var creatorName = assignment.getString("creator_name");
            var listId = assignment.getString("list_id");
            var assignmentDto = AssignmentDto.builder()
                    .assigneeId(assigneeId)
                    .assigneeName(assigneeName)
                    .creatorId(creatorId)
                    .creatorName(creatorName)
                    .listId(listId)
                    .build();
            if (assigneeId.equals(creatorId)) {
                personalResult.correctAssignment(assignmentDto);
            } else {
                personalResult.incorrectAssignment(assignmentDto);
            }
        });

        return personalResults.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build()));
    }

    public Future<String> createQuiz(SQLConnection connection, String name, String creatorId, Instant deadline) {
        return Future.future(promise -> {
            var params = new JsonArray().add(name).add(toUuid(creatorId)).add(deadline);
            connection.updateWithParams(CREATE_QUIZ_TEMPLATE, params, asyncResult -> {
                if (asyncResult.failed()) {
                    var cause = asyncResult.cause();
                    log.error("Unable to execute query \"{}\"", CREATE_QUIZ_TEMPLATE, cause);
                    promise.fail(cause);
                    return;
                }

                var quizId = asyncResult.result().getKeys().getString(3).replace("-", "");
                log.debug("Query \"{}\" produced result \"{}\"", CREATE_QUIZ_TEMPLATE, quizId);
                promise.complete(quizId);
            });
        });
    }

    public Future<Void> completeQuiz(SQLConnection connection, String accountId, String quizId) {
        return Future.future(promise ->
                connection.updateWithParams(COMPLETE_QUIZ_TEMPLATE, new JsonArray().add(toUuid(accountId)).add(toUuid(quizId)), asyncComplete -> {
                    if (asyncComplete.failed()) {
                        var cause = asyncComplete.cause();
                        log.error("Unable to execute query \"{}\" with parameters \"{}\" and \"{}\"", COMPLETE_QUIZ_TEMPLATE, accountId, quizId, cause);
                        promise.fail(cause);
                        return;
                    }

                    var numberOfAffectedRows = asyncComplete.result().getUpdated();
                    log.debug("Affected {} rows by executing query \"{}\"", numberOfAffectedRows, COMPLETE_QUIZ_TEMPLATE);

                    promise.complete();
                }));
    }

    public Future<String> createList(SQLConnection connection, String accountId, String quizId) {
        return Future.future(promise ->
                connection.updateWithParams(CREATE_LIST_TEMPLATE, new JsonArray().add(toUuid(accountId)).add(toUuid(quizId)), asyncUpdate -> {
                    if (asyncUpdate.failed()) {
                        var cause = asyncUpdate.cause();
                        log.error("Unable to execute query \"{}\"", CREATE_LIST_TEMPLATE, cause);
                        promise.fail(cause);
                        return;
                    }

                    if (asyncUpdate.result().getUpdated() == 0) {
                        var errorMessage = String.format("Account \"%s\" already has a list for quiz \"%s\"", accountId, quizId);
                        promise.fail(new ConflictException(errorMessage));
                        return;
                    }

                    var listId = asyncUpdate.result().getKeys().getString(1).replace("-", "");

                    log.debug("Created new list \"{}\"", listId);

                    promise.complete(listId);
                }));
    }

    public Future<JsonArray> getAllParticipants(SQLConnection connection, String quizId, String accountId) {
        return Future.future(promise ->
                connection.queryWithParams(GET_PARTICIPANTS_TEMPLATE, new JsonArray().add(toUuid(accountId)).add(toUuid(quizId)), asyncParticipants -> {
                    if (asyncParticipants.failed()) {
                        var cause = asyncParticipants.cause();
                        log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_PARTICIPANTS_TEMPLATE, quizId, cause);
                        promise.fail(cause);
                        return;
                    }

                    log.debug("Retrieved all participants for quiz");

                    var quizzes = asyncParticipants.result().getResults().stream()
                            .map(participant -> participantArrayToJsonObject(participant, accountId))
                            .collect(Collectors.toList());

                    promise.complete(new JsonArray(quizzes));
                }));
    }

    private JsonObject participantArrayToJsonObject(JsonArray array, String accountId) {
        var participantId = array.getString(0);
        return new JsonObject()
                .put("id", participantId)
                .put("name", array.getString(1))
                .put("listHasDraftStatus", array.getBoolean(2))
                .put("assignedListId", array.getString(3))
                .put("isOwnAccount", participantId.equals(accountId));
    }
}
