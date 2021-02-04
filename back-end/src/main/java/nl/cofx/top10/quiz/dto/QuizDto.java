package nl.cofx.top10.quiz.dto;

import java.time.Instant;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuizDto {

    Integer id;
    String name;
    boolean isActive;
    Integer creatorId;
    boolean isCreator;
    Instant deadline;
    String externalId;
    Integer personalListId;
    Boolean personalListHasDraftStatus;

    public static QuizDto fromJsonObject(JsonObject jsonObject) {
        return QuizDto.builder()
                .id(jsonObject.getInteger("id"))
                .name(jsonObject.getString("name"))
                .isActive(jsonObject.getBoolean("isActive"))
                .creatorId(jsonObject.getInteger("creatorId"))
                .deadline(jsonObject.getInstant("deadline"))
                .externalId(jsonObject.getString("externalId"))
                .personalListId(jsonObject.getInteger("personalListId"))
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
                .put("deadline", deadline)
                .put("externalId", externalId);

        if (personalListId != null) {
            jsonObject.put("personalListId", personalListId);
        }

        if (personalListHasDraftStatus != null) {
            jsonObject.put("personalListHasDraftStatus", personalListHasDraftStatus);
        }

        return jsonObject;
    }
}
