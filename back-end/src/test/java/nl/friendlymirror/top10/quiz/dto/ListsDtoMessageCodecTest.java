package nl.friendlymirror.top10.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class ListsDtoMessageCodecTest {

    private final ListsDtoMessageCodec codec = new ListsDtoMessageCodec();

    @Test
    public void encodesListsDto() {
        var listDto = ListDto.builder()
                .listId(123)
                .accountId(456)
                .assigneeId(789)
                .quizId(321)
                .hasDraftStatus(true)
                .videos(List.of(VideoDto.builder()
                        .id(654)
                        .url("http://www.example.org")
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
