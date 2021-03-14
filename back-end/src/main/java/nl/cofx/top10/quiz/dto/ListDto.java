package nl.cofx.top10.quiz.dto;

import java.util.List;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ListDto {

    String id;
    String creatorId;
    String creatorName;
    boolean isOwnList;
    String quizId;
    boolean isActiveQuiz;
    String assigneeId;
    String assigneeName;
    Boolean hasDraftStatus;
    List<VideoDto> videos;

    public static ListDto fromJsonObject(JsonObject jsonObject) {
        return ListDto.builder()
                .id(jsonObject.getString("id"))
                .quizId(jsonObject.getString("quizId"))
                .isActiveQuiz(jsonObject.getBoolean("isActiveQuiz"))
                .creatorId(jsonObject.getString("creatorId"))
                .creatorName(jsonObject.getString("creatorName"))
                .isOwnList(jsonObject.getBoolean("isOwnList"))
                .assigneeId(jsonObject.getString("assigneeId"))
                .assigneeName(jsonObject.getString("assigneeName"))
                .hasDraftStatus(jsonObject.getBoolean("hasDraftStatus"))
                .videos(VideoDto.fromJsonArray(jsonObject.getJsonArray("videos")))
                .build();
    }

    public JsonObject toJsonObject() {
        var jsonObject = new JsonObject()
                .put("id", id)
                .put("isActiveQuiz", isActiveQuiz)
                .put("isOwnList", isOwnList);

        if (creatorId != null)
            jsonObject.put("creatorId", creatorId);

        if (creatorName != null)
            jsonObject.put("creatorName", creatorName);

        if (assigneeId != null)
            jsonObject.put("assigneeId", assigneeId);

        if (assigneeName != null)
            jsonObject.put("assigneeName", assigneeName);

        if (quizId != null)
            jsonObject.put("quizId", quizId);

        if (hasDraftStatus != null)
            jsonObject.put("hasDraftStatus", hasDraftStatus);

        if (videos != null)
            jsonObject.put("videos", VideoDto.toJsonArray(videos));

        return jsonObject;
    }
}
