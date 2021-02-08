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
    Map<String, PersonalResultDto> personalResults = new HashMap<>();

    private int getNumberOfCorrectAssignments(PersonalResultDto personalResultDto) {
        return personalResultDto.getCorrectAssignments().size();
    }

    public List<RankingEntryDto> getRanking() {
        var results = new ArrayList<>(personalResults.values());
        results.sort(Comparator.comparing(this::getNumberOfCorrectAssignments).reversed());

        int rank = 0;
        int skipRanks = 1;
        int previousNumberOfCorrectAssignments = Integer.MAX_VALUE;
        var ranking = new ArrayList<RankingEntryDto>(results.size());
        for (PersonalResultDto personalResult : results) {
            var numberOfCorrectAssignments = getNumberOfCorrectAssignments(personalResult);
            if (previousNumberOfCorrectAssignments > numberOfCorrectAssignments) {
                rank = rank + skipRanks;
                skipRanks = 1;
            } else {
                skipRanks++;
            }
            previousNumberOfCorrectAssignments = numberOfCorrectAssignments;

            var rankingEntry = RankingEntryDto.builder()
                    .externalAccountId(personalResult.getExternalAccountId())
                    .name(personalResult.getName())
                    .numberOfCorrectAssignments(numberOfCorrectAssignments)
                    .rank(rank)
                    .build();
            ranking.add(rankingEntry);
        }

        ranking.sort((r1, r2) -> {
            if (r1.getRank() == r2.getRank()) {
                return r1.getName().compareToIgnoreCase(r2.getName());
            } else {
                return r1.getRank() - r2.getRank();
            }
        });

        return ranking;
    }

    public static ResultSummaryDto fromJsonObject(JsonObject jsonObject) {
        var personalResults = PersonalResultDto.toPersonalResults(jsonObject.getJsonObject("personalResults"));
        return ResultSummaryDto.builder()
                .quizId(jsonObject.getString("quizId"))
                .personalResults(personalResults)
                .build();
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("quizId", quizId)
                .put("personalResults", PersonalResultDto.toJsonObject(personalResults))
                .put("ranking", new JsonArray(getRanking().stream()
                        .map(RankingEntryDto::toJsonObject)
                        .collect(Collectors.toList())));
    }
}
