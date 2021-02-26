package nl.cofx.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class ListsDtoMessageCodecTest {

    private final ListsDtoMessageCodec codec = new ListsDtoMessageCodec();

    @Test
    public void encodesListsDto() {
        var listDto = ListDto.builder()
                .id("123")
                .creatorId("456")
                .creatorName("John Doe")
                .assigneeId("abcd")
                .quizId("321")
                .isActiveQuiz(true)
                .hasDraftStatus(true)
                .videos(List.of(VideoDto.builder()
                        .id("654")
                        .url("http://www.example.org/abcde")
                        .referenceId("abcde")
                        .build()))
                .build();
        var listsDto = ListsDto.builder()
                .list(listDto)
                .build();

        var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, listsDto);

        assertThat(codec.decodeFromWire(0, buffer)).isEqualTo(listsDto);
    }
}
