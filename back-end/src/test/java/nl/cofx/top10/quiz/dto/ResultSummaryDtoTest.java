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
                                .accountId("4")
                                .name("FourA")
                                .correctAssignment(assignment())
                                .build(),
                        "5",
                        PersonalResultDto.builder()
                                .accountId("5")
                                .name("FourB")
                                .correctAssignment(assignment())
                                .build(),
                        "3",
                        PersonalResultDto.builder()
                                .accountId("3")
                                .name("Three")
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .build(),
                        "1",
                        PersonalResultDto.builder()
                                .accountId("1")
                                .name("OneA")
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .build(),
                        "6",
                        PersonalResultDto.builder()
                                .accountId("6")
                                .name("Six")
                                .build(),
                        "2",
                        PersonalResultDto.builder()
                                .accountId("2")
                                .name("OneB")
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .correctAssignment(assignment())
                                .build()))
                .build();

        assertThat(resultSummaryDto.getRanking()).containsExactly(
                RankingEntryDto.builder()
                        .accountId("1")
                        .name("OneA")
                        .rank(1)
                        .numberOfCorrectAssignments(3)
                        .build(),
                RankingEntryDto.builder()
                        .accountId("2")
                        .name("OneB")
                        .rank(1)
                        .numberOfCorrectAssignments(3)
                        .build(),
                RankingEntryDto.builder()
                        .accountId("3")
                        .name("Three")
                        .rank(3)
                        .numberOfCorrectAssignments(2)
                        .build(),
                RankingEntryDto.builder()
                        .accountId("4")
                        .name("FourA")
                        .rank(4)
                        .numberOfCorrectAssignments(1)
                        .build(),
                RankingEntryDto.builder()
                        .accountId("5")
                        .name("FourB")
                        .rank(4)
                        .numberOfCorrectAssignments(1)
                        .build(),
                RankingEntryDto.builder()
                        .accountId("6")
                        .name("Six")
                        .rank(6)
                        .numberOfCorrectAssignments(0)
                        .build());
    }

    private AssignmentDto assignment() {
        return AssignmentDto.builder().build();
    }
}
