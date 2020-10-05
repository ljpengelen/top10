package nl.friendlymirror.top10.quiz.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VideoDto {

    Integer videoId;
    String url;

    public static VideoDto fromJsonObject(JsonObject jsonObject) {
        return VideoDto.builder()
                .videoId(jsonObject.getInteger("videoId"))
                .url(jsonObject.getString("url"))
                .build();
    }

    public static List<VideoDto> fromJsonArray(JsonArray jsonArray) {
        var size = jsonArray.size();
        var videos = new ArrayList<VideoDto>(size);

        for (var i = 0; i < size; ++i) {
            videos.add(VideoDto.fromJsonObject(jsonArray.getJsonObject(i)));
        }

        return videos;
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("videoId", videoId)
                .put("url", url);
    }

    public static JsonArray toJsonArray(List<VideoDto> videoDtos) {
        var jsonObjects = videoDtos.stream()
                .map(VideoDto::toJsonObject)
                .collect(Collectors.toList());

        return new JsonArray(jsonObjects);
    }
}
