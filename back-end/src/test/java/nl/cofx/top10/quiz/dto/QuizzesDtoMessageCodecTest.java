package nl.cofx.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class QuizzesDtoMessageCodecTest {

    private final QuizzesDtoMessageCodec codec = new QuizzesDtoMessageCodec();

    @Test
    public void encodesQuizzesDto() {
        var quizDto = QuizDto.builder()
                .id(123)
                .name("abcd")
                .isActive(true)
                .creatorId(456)
                .isCreator(false)
                .deadline(Instant.now())
                .externalId("efgh")
                .personalListId(789)
                .personalListHasDraftStatus(true)
                .build();
        var quizzesDto = QuizzesDto.builder()
                .quiz(quizDto)
                .build();

        var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, quizzesDto);

        assertThat(codec.decodeFromWire(0, buffer)).isEqualTo(quizzesDto);
    }
}
