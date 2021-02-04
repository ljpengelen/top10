package nl.cofx.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class QuizDtoMessageCodecTest {

    private final QuizDtoMessageCodec codec = new QuizDtoMessageCodec();

    @Test
    public void encodesQuizDto() {
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

        var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, quizDto);

        assertThat(codec.decodeFromWire(0, buffer)).isEqualTo(quizDto);
    }
}
