package nl.cofx.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class ResultSummaryDtoMessageCodecTest {

    private final ResultSummaryDtoMessageCodec codec = new ResultSummaryDtoMessageCodec();

    @Test
    public void encodesResultSummaryDto() {
        var resultSummaryDto = ResultSummaryDto.builder()
                .quizId("abc")
                .personalResults(List.of(PersonalResultDto.builder()
                        .accountId(321)
                        .name("John Doe")
                        .correctAssignments(List.of(AssignmentDto.builder()
                                .listId(456)
                                .creatorId(789)
                                .creatorName("Jane Doe")
                                .assigneeId(789)
                                .assigneeName("Jane Doe")
                                .build()))
                        .incorrectAssignments(List.of(AssignmentDto.builder()
                                .listId(654)
                                .creatorId(987)
                                .assigneeName("Jim Doe")
                                .assigneeId(789)
                                .assigneeName("Jane Doe")
                                .build()))
                        .build()))
                .build();

        var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, resultSummaryDto);

        assertThat(codec.decodeFromWire(0, buffer)).isEqualTo(resultSummaryDto);
    }
}
