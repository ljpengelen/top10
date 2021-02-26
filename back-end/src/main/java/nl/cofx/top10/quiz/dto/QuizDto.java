package nl.cofx.top10.quiz.dto;

import java.time.Instant;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuizDto {

    String id;
    String name;
    boolean isActive;
    String creatorId;
    boolean isCreator;
    Instant deadline;
    String personalListId;
    Boolean personalListHasDraftStatus;

    public static QuizDto fromJsonObject(JsonObject jsonObject) {
        return QuizDto.builder()
                .id(jsonObject.getString("id"))
                .name(jsonObject.getString("name"))
                .isActive(jsonObject.getBoolean("isActive"))
                .creatorId(jsonObject.getString("creatorId"))
                .deadline(jsonObject.getInstant("deadline"))
                .personalListId(jsonObject.getString("personalListId"))
                .personalListHasDraftStatus(jsonObject.getBoolean("personalListHasDraftStatus"))
                .build();
    }

    public JsonObject toJsonObject() {
        var jsonObject = new JsonObject()
                .put("id", id)
                .put("name", name)
                .put("isActive", isActive)
                .put("creatorId", creatorId)
                .put("isCreator", isCreator)
                .put("deadline", deadline);

        if (personalListId != null) {
            jsonObject.put("personalListId", personalListId);
        }

        if (personalListHasDraftStatus != null) {
            jsonObject.put("personalListHasDraftStatus", personalListHasDraftStatus);
        }

        return jsonObject;
    }
}
