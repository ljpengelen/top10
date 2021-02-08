package nl.cofx.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ResultSummaryDtoTest {

    @Test
    void computesFromPersonalResultsRanking() {
        var resultSummaryDto = ResultSummaryDto.builder()
                .personalResults(Map.of(
                        "4",
                        PersonalResultDto.builder()
                                .externalAccountId("4")
                                .name("Four")
                                .correctAssignment(assignment())
                                .build(),
                        "5",
                        PersonalResultDto.builder()
                                .externalAccountId("5")
                                .name("Five")
                                .correctAssignment(assignment())
                                .build(),
                        "3",
                        PersonalResultDto.builder()
                                .externalAccountId("3")
                                .name("Three")
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .build(),
                        "1",
                        PersonalResultDto.builder()
                                .externalAccountId("1")
                                .name("One")
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .build(),
                        "6",
                        PersonalResultDto.builder()
                                .externalAccountId("6")
                                .name("Six")
                                .build(),
                        "2",
                        PersonalResultDto.builder()
                                .externalAccountId("2")
                                .name("Two")
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .build()))
                .build();

        assertThat(resultSummaryDto.getRanking()).containsExactly(
                RankingEntryDto.builder()
                        .externalAccountId("1")
                        .name("One")
                        .rank(1)
                        .numberOfCorrectAssignments(3)
                        .build(),
                RankingEntryDto.builder()
                        .externalAccountId("2")
                        .name("Two")
                        .rank(1)
                        .numberOfCorrectAssignments(3)
                        .build(),
                RankingEntryDto.builder()
                        .externalAccountId("3")
                        .name("Three")
                        .rank(3)
                        .numberOfCorrectAssignments(2)
                        .build(),
                RankingEntryDto.builder()
                        .externalAccountId("4")
                        .name("Four")
                        .rank(4)
                        .numberOfCorrectAssignments(1)
                        .build(),
                RankingEntryDto.builder()
                        .externalAccountId("5")
                        .name("Five")
                        .rank(4)
                        .numberOfCorrectAssignments(1)
                        .build(),
                RankingEntryDto.builder()
                        .externalAccountId("6")
                        .name("Six")
                        .rank(6)
                        .numberOfCorrectAssignments(0)
                        .build());
    }

    private AssignmentDto assignment() {
        return AssignmentDto.builder().build();
    }
}
