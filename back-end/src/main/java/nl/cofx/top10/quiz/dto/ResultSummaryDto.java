package nl.cofx.top10.quiz.dto;

import java.util.*;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResultSummaryDto {

    String quizId;
    @Builder.Default
    List<PersonalResultDto> personalResults = new ArrayList<>();

    private int getNumberOfCorrectAssignments(PersonalResultDto personalResultDto) {
        return personalResultDto.getCorrectAssignments().size();
    }

    public List<RankingEntryDto> getRanking() {
        personalResults.sort(Comparator.comparing(this::getNumberOfCorrectAssignments).reversed());

        int rank = 0;
        int skipRanks = 1;
        int previousNumberOfCorrectAssignments = Integer.MAX_VALUE;
        var ranking = new ArrayList<RankingEntryDto>(personalResults.size());
        for (PersonalResultDto personalResult : personalResults) {
            var numberOfCorrectAssignments = getNumberOfCorrectAssignments(personalResult);
            if (previousNumberOfCorrectAssignments > numberOfCorrectAssignments) {
                rank = rank + skipRanks;
                skipRanks = 1;
            } else {
                skipRanks++;
            }
            previousNumberOfCorrectAssignments = numberOfCorrectAssignments;

            var rankingEntry = RankingEntryDto.builder()
                    .accountId(personalResult.getAccountId())
                    .name(personalResult.getName())
                    .numberOfCorrectAssignments(numberOfCorrectAssignments)
                    .rank(rank)
                    .build();
            ranking.add(rankingEntry);
        }

        return ranking;
    }

    public static ResultSummaryDto fromJsonObject(JsonObject jsonObject) {
        var personalResults = PersonalResultDto.fromJsonArray(jsonObject.getJsonArray("personalResults"));
        return ResultSummaryDto.builder()
                .quizId(jsonObject.getString("quizId"))
                .personalResults(personalResults)
                .build();
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("quizId", quizId)
                .put("personalResults", PersonalResultDto.toJsonArray(personalResults))
                .put("ranking", new JsonArray(getRanking().stream()
                        .map(RankingEntryDto::toJsonObject)
                        .collect(Collectors.toList())));
    }
}
