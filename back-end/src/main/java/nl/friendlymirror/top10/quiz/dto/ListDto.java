package nl.friendlymirror.top10.quiz.dto;

import java.util.List;

import io.vertx.core.json.JsonObject;
import lombok.*;

@Value
@Builder(toBuilder = true)
public class ListDto {

    Integer id;
    Integer accountId;
    Integer quizId;
    Integer assigneeId;
    Boolean hasDraftStatus;
    @Singular
    List<VideoDto> videos;

    public static ListDto fromJsonObject(JsonObject jsonObject) {
        return ListDto.builder()
                .id(jsonObject.getInteger("id"))
                .quizId(jsonObject.getInteger("quizId"))
                .accountId(jsonObject.getInteger("accountId"))
                .assigneeId(jsonObject.getInteger("assigneeId"))
                .hasDraftStatus(jsonObject.getBoolean("hasDraftStatus"))
                .videos(VideoDto.fromJsonArray(jsonObject.getJsonArray("videos")))
                .build();
    }

    public JsonObject toJsonObject() {
        var jsonObject = new JsonObject().put("id", id);

        if (accountId != null)
            jsonObject.put("accountId", accountId);

        if (assigneeId != null)
            jsonObject.put("assigneeId", assigneeId);

        if (quizId != null)
            jsonObject.put("quizId", quizId);

        if (hasDraftStatus != null)
            jsonObject.put("hasDraftStatus", hasDraftStatus);

        if (videos != null)
            jsonObject.put("videos", VideoDto.toJsonArray(videos));

        return jsonObject;
    }
}
