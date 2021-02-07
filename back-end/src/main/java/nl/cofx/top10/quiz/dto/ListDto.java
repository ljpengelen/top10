package nl.cofx.top10.quiz.dto;

import java.util.List;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ListDto {

    Integer id;
    Integer accountId;
    Integer quizId;
    String externalQuizId;
    String externalAssigneeId;
    String assigneeName;
    Boolean hasDraftStatus;
    List<VideoDto> videos;

    public static ListDto fromJsonObject(JsonObject jsonObject) {
        return ListDto.builder()
                .id(jsonObject.getInteger("id"))
                .quizId(jsonObject.getInteger("quizId"))
                .externalQuizId(jsonObject.getString("externalQuizId"))
                .accountId(jsonObject.getInteger("accountId"))
                .externalAssigneeId(jsonObject.getString("externalAssigneeId"))
                .assigneeName(jsonObject.getString("assigneeName"))
                .hasDraftStatus(jsonObject.getBoolean("hasDraftStatus"))
                .videos(VideoDto.fromJsonArray(jsonObject.getJsonArray("videos")))
                .build();
    }

    public JsonObject toJsonObject() {
        var jsonObject = new JsonObject().put("id", id);

        if (accountId != null)
            jsonObject.put("accountId", accountId);

        if (externalAssigneeId != null)
            jsonObject.put("externalAssigneeId", externalAssigneeId);

        if (assigneeName != null)
            jsonObject.put("assigneeName", assigneeName);

        if (quizId != null)
            jsonObject.put("quizId", quizId);

        if (externalQuizId != null)
            jsonObject.put("externalQuizId", externalQuizId);

        if (hasDraftStatus != null)
            jsonObject.put("hasDraftStatus", hasDraftStatus);

        if (videos != null)
            jsonObject.put("videos", VideoDto.toJsonArray(videos));

        return jsonObject;
    }
}
