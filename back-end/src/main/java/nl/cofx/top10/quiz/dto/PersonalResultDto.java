package nl.cofx.top10.quiz.dto;

import java.util.*;

import io.vertx.core.json.JsonObject;
import lombok.*;

@Value
@Builder
public class PersonalResultDto {

    String externalAccountId;
    String name;
    @Singular
    List<AssignmentDto> correctAssignments;
    @Singular
    List<AssignmentDto> incorrectAssignments;

    public static PersonalResultDto toPersonalResult(JsonObject jsonObject) {
        var correctAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("correctAssignments"));
        var incorrectAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("incorrectAssignments"));
        return PersonalResultDto.builder()
                .externalAccountId(jsonObject.getString("externalAccountId"))
                .name(jsonObject.getString("name"))
                .correctAssignments(correctAssignments)
                .incorrectAssignments(incorrectAssignments)
                .build();
    }

    public static Map<String, PersonalResultDto> toPersonalResults(JsonObject jsonObject) {
        var personalResults = new HashMap<String, PersonalResultDto>();

        jsonObject.forEach(e -> {
            var externalAccountId = e.getKey();
            var personalResultAsJsonObject = (JsonObject) e.getValue();
            var personalResult = toPersonalResult(personalResultAsJsonObject);

            personalResults.put(externalAccountId, personalResult);
        });

        return personalResults;
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("externalAccountId", externalAccountId)
                .put("name", name)
                .put("correctAssignments", AssignmentDto.toJsonArray(correctAssignments))
                .put("incorrectAssignments", AssignmentDto.toJsonArray(incorrectAssignments));
    }

    public static JsonObject toJsonObject(Map<String, PersonalResultDto> personalResults) {
        var jsonObject = new JsonObject();
        personalResults.forEach((externalAccountId, personalResultDto) ->
                jsonObject.put(externalAccountId, personalResultDto.toJsonObject()));

        return jsonObject;
    }
}
