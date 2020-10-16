package nl.friendlymirror.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class ListDtoMessageCodecTest {

    private final ListDtoMessageCodec codec = new ListDtoMessageCodec();

    @Test
    public void encodesListDto() {
        var listDto = ListDto.builder()
                .listId(123)
                .accountId(456)
                .assigneeId(789)
                .quizId(321)
                .hasDraftStatus(true)
                .videos(List.of(VideoDto.builder()
                        .videoId(654)
                        .url("http://www.example.org")
                        .build()))
                .build();

        var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, listDto);

        assertThat(codec.decodeFromWire(0, buffer)).isEqualTo(listDto);
    }
}