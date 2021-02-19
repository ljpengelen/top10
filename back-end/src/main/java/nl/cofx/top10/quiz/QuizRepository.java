package nl.cofx.top10.quiz;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.Promise;
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
            "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline, q.external_id, l.list_id, l.has_draft_status FROM quiz q "
            + "JOIN list l ON l.quiz_id = q.quiz_id "
            + "WHERE l.account_id = ?";
    private static final String GET_ONE_QUIZ_TEMPLATE = "SELECT q.quiz_id, q.name, q.is_active, q.creator_id, q.deadline, q.external_id, l.list_id, l.has_draft_status FROM quiz q "
                                                        + "LEFT JOIN list l ON l.quiz_id = q.quiz_id AND l.account_id = ? "
                                                        + "WHERE q.external_id = ?";
    private static final String CREATE_QUIZ_TEMPLATE = "INSERT INTO quiz (name, is_active, creator_id, deadline, external_id) VALUES (?, true, ?, ?, ?)";
    private static final String COMPLETE_QUIZ_TEMPLATE = "UPDATE quiz SET is_active = false WHERE creator_id = ? AND external_id = ?";
    private static final String CREATE_LIST_TEMPLATE = "INSERT INTO list (account_id, quiz_id, has_draft_status) "
                                                       + "VALUES (?, (SELECT quiz_id from quiz WHERE external_id = ?), true) "
                                                       + "ON CONFLICT DO NOTHING";
    private static final String GET_PARTICIPANTS_TEMPLATE = "SELECT a.external_id, a.name, l.has_draft_status FROM account a "
                                                            + "JOIN list l ON l.account_id = a.account_id "
                                                            + "JOIN quiz q ON l.quiz_id = q.quiz_id "
                                                            + "WHERE q.external_id = ?";
    private static final String GET_QUIZ_RESULT_TEMPLATE =
            "SELECT ass.list_id, "
            + "assigner_acc.external_id AS external_assigner_id, assigner_acc.name AS assigner_name, "
            + "assignee_acc.external_id AS external_assignee_id, assignee_acc.name AS assignee_name, "
            + "creator_acc.external_id AS external_creator_id, creator_acc.name AS creator_name FROM quiz q "
            + "JOIN list l ON l.quiz_id = q.quiz_id "
            + "JOIN assignment ass ON ass.list_id = l.list_id "
            + "JOIN account creator_acc ON creator_acc.account_id = l.account_id "
            + "JOIN account assigner_acc ON assigner_acc.account_id = ass.account_id "
            + "JOIN account assignee_acc ON assignee_acc.account_id = ass.assignee_id "
            + "WHERE q.external_id = ?";

    public Future<QuizzesDto> getAllQuizzes(SQLConnection connection, Integer accountId) {
        var promise = Promise.<QuizzesDto> promise();

        connection.queryWithParams(GET_ALL_QUIZZES_TEMPLATE, new JsonArray().add(accountId), asyncQuizzes -> {
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
        });

        return promise.future();
    }

    private QuizDto toQuizDto(JsonArray array, Integer accountId) {
        var creatorId = array.getInteger(3);
        var quizDtoBuilder = QuizDto.builder()
                .id(array.getInteger(0))
                .name(array.getString(1))
                .isActive(array.getBoolean(2))
                .creatorId(creatorId)
                .isCreator(creatorId.equals(accountId))
                .deadline(array.getInstant(4))
                .externalId(array.getString(5));

        var personalListId = array.getInteger(6);
        if (personalListId != null) {
            quizDtoBuilder.personalListId(personalListId);
        }

        var personalListHasDraftStatus = array.getBoolean(7);
        if (personalListHasDraftStatus != null) {
            quizDtoBuilder.personalListHasDraftStatus(personalListHasDraftStatus);
        }

        return quizDtoBuilder.build();
    }

    public Future<QuizDto> getQuiz(SQLConnection connection, String externalId, Integer accountId) {
        var promise = Promise.<QuizDto> promise();

        connection.querySingleWithParams(GET_ONE_QUIZ_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncQuiz -> {
            if (asyncQuiz.failed()) {
                var cause = asyncQuiz.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_ONE_QUIZ_TEMPLATE, externalId, cause);
                promise.fail(cause);
                return;
            }

            if (asyncQuiz.result() == null) {
                log.debug("Quiz with external ID \"{}\" not found", externalId);
                promise.fail(new NotFoundException(String.format("Quiz with external ID \"%s\" not found", externalId)));
            } else {
                var quiz = toQuizDto(asyncQuiz.result(), accountId);
                log.debug("Retrieved quiz by external ID \"{}\": \"{}\"", externalId, quiz);
                promise.complete(quiz);
            }
        });

        return promise.future();
    }

    public Future<ResultSummaryDto> getQuizResult(SQLConnection connection, String externalId) {
        var promise = Promise.<ResultSummaryDto> promise();

        connection.queryWithParams(GET_QUIZ_RESULT_TEMPLATE, new JsonArray().add(externalId), asyncQuizResult -> {
            if (asyncQuizResult.failed()) {
                var cause = asyncQuizResult.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_QUIZ_RESULT_TEMPLATE, externalId, cause);
                promise.fail(cause);
                return;
            }

            var rows = asyncQuizResult.result().getRows();
            var quizResult = assignmentsToQuizResult(externalId, rows);
            log.debug("Retrieved result for quiz with external ID \"{}\": \"{}\"", externalId, quizResult);
            promise.complete(quizResult);
        });

        return promise.future();
    }

    private ResultSummaryDto assignmentsToQuizResult(String externalId, List<JsonObject> assignments) {
        return ResultSummaryDto.builder()
                .quizId(externalId)
                .personalResults(toPersonalResults(assignments))
                .build();
    }

    private Map<String, PersonalResultDto> toPersonalResults(List<JsonObject> assignments) {
        var personalResults = new HashMap<String, PersonalResultDto.PersonalResultDtoBuilder>();

        assignments.forEach(assignment -> {
            var externalAccountId = assignment.getString("external_assigner_id");
            var name = assignment.getString("assigner_name");
            var personalResult = personalResults.computeIfAbsent(externalAccountId, key ->
                    PersonalResultDto.builder()
                            .externalAccountId(key)
                            .name(name));
            var externalAssigneeId = assignment.getString("external_assignee_id");
            var assigneeName = assignment.getString("assignee_name");
            var externalCreatorId = assignment.getString("external_creator_id");
            var creatorName = assignment.getString("creator_name");
            var listId = assignment.getInteger("list_id");
            var assignmentDto = AssignmentDto.builder()
                    .externalAssigneeId(externalAssigneeId)
                    .assigneeName(assigneeName)
                    .externalCreatorId(externalCreatorId)
                    .creatorName(creatorName)
                    .listId(listId)
                    .build();
            if (externalAssigneeId.equals(externalCreatorId)) {
                personalResult.correctAssignment(assignmentDto);
            } else {
                personalResult.incorrectAssignment(assignmentDto);
            }
        });

        return personalResults.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build()));
    }

    public Future<Integer> createQuiz(SQLConnection connection, String name, Integer creatorId, Instant deadline, String externalId) {
        var promise = Promise.<Integer> promise();

        var params = new JsonArray().add(name).add(creatorId).add(deadline).add(externalId);
        connection.updateWithParams(CREATE_QUIZ_TEMPLATE, params, asyncResult -> {
            if (asyncResult.failed()) {
                var cause = asyncResult.cause();
                log.error("Unable to execute query \"{}\"", CREATE_QUIZ_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            var quizId = asyncResult.result().getKeys().getInteger(0);
            log.debug("Query \"{}\" produced result \"{}\"", CREATE_QUIZ_TEMPLATE, quizId);
            promise.complete(quizId);
        });

        return promise.future();
    }

    public Future<Void> completeQuiz(SQLConnection connection, Integer accountId, String externalId) {
        var promise = Promise.<Void> promise();

        connection.updateWithParams(COMPLETE_QUIZ_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncComplete -> {
            if (asyncComplete.failed()) {
                var cause = asyncComplete.cause();
                log.error("Unable to execute query \"{}\" with parameters \"{}\" and \"{}\"", COMPLETE_QUIZ_TEMPLATE, accountId, externalId, cause);
                promise.fail(cause);
                return;
            }

            var numberOfAffectedRows = asyncComplete.result().getUpdated();
            log.debug("Affected {} rows by executing query \"{}\"", numberOfAffectedRows, COMPLETE_QUIZ_TEMPLATE);

            promise.complete();
        });

        return promise.future();
    }

    public Future<Integer> createList(SQLConnection connection, Integer accountId, String externalId) {
        var promise = Promise.<Integer> promise();

        connection.updateWithParams(CREATE_LIST_TEMPLATE, new JsonArray().add(accountId).add(externalId), asyncUpdate -> {
            if (asyncUpdate.failed()) {
                var cause = asyncUpdate.cause();
                log.error("Unable to execute query \"{}\"", CREATE_LIST_TEMPLATE, cause);
                promise.fail(cause);
                return;
            }

            if (asyncUpdate.result().getUpdated() == 0) {
                var errorMessage = String.format("Account with ID \"%d\" already has a list for quiz with external ID \"%s\"", accountId, externalId);
                promise.fail(new ConflictException(errorMessage));
                return;
            }

            var listId = asyncUpdate.result().getKeys().getInteger(0);

            log.debug("Created new list with ID \"{}\"", listId);

            promise.complete(listId);
        });

        return promise.future();
    }

    public Future<JsonArray> getAllParticipants(SQLConnection connection, String externalId) {
        var promise = Promise.<JsonArray> promise();

        connection.queryWithParams(GET_PARTICIPANTS_TEMPLATE, new JsonArray().add(externalId), asyncParticipants -> {
            if (asyncParticipants.failed()) {
                var cause = asyncParticipants.cause();
                log.error("Unable to execute query \"{}\" with parameter \"{}\"", GET_PARTICIPANTS_TEMPLATE, externalId, cause);
                promise.fail(cause);
                return;
            }

            log.debug("Retrieved all participants for quiz");

            var quizzes = asyncParticipants.result().getResults().stream()
                    .map(this::participantArrayToJsonObject)
                    .collect(Collectors.toList());

            promise.complete(new JsonArray(quizzes));
        });

        return promise.future();
    }

    private JsonObject participantArrayToJsonObject(JsonArray array) {
        return new JsonObject()
                .put("id", array.getString(0))
                .put("name", array.getString(1))
                .put("listHasDraftStatus", array.getBoolean(2));
    }
}
