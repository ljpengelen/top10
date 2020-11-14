package nl.friendlymirror.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class ResultSummaryDtoMessageCodecTest {

    private final ResultSummaryDtoMessageCodec codec = new ResultSummaryDtoMessageCodec();

    @Test
    public void encodesResultSummaryDto() {
        var resultSummaryDto = ResultSummaryDto.builder()
                .quizId(123)
                .personalResults(List.of(PersonalResultDto.builder()
                        .accountId(321)
                        .correctAssignments(List.of(AssignmentDto.builder()
                                .listId(456)
                                .creatorId(789)
                                .assigneeId(789)
                                .build()))
                        .incorrectAssignments(List.of(AssignmentDto.builder()
                                .listId(654)
                                .creatorId(987)
                                .assigneeId(789)
                                .build()))
                        .numberOfCorrectAssignments(1)
                        .build()))
                .build();

        var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, resultSummaryDto);

        assertThat(codec.decodeFromWire(0, buffer)).isEqualTo(resultSummaryDto);
    }
}
