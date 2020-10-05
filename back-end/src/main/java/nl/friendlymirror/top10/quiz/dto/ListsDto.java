package nl.friendlymirror.top10.quiz.dto;

import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import lombok.*;

@Value
@Builder
public class ListsDto {

    @Singular
    List<ListDto> lists;

    public JsonArray toJsonArray() {
        var jsonObjects = lists.stream()
                .map(ListDto::toJsonObject)
                .collect(Collectors.toList());

        return new JsonArray(jsonObjects);
    }

    public static ListsDto fromJsonArray(JsonArray jsonArray) {
        var builder = ListsDto.builder();

        for (var i = 0; i < jsonArray.size(); ++i) {
            builder.list(ListDto.fromJsonObject(jsonArray.getJsonObject(i)));
        }

        return builder.build();
    }
}
