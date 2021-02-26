package nl.cofx.top10.quiz.dto;

import java.util.*;

import io.vertx.core.json.JsonObject;
import lombok.*;

@Value
@Builder
public class PersonalResultDto {

    String accountId;
    String name;
    @Singular
    List<AssignmentDto> correctAssignments;
    @Singular
    List<AssignmentDto> incorrectAssignments;

    public static PersonalResultDto toPersonalResult(JsonObject jsonObject) {
        var correctAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("correctAssignments"));
        var incorrectAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("incorrectAssignments"));
        return PersonalResultDto.builder()
                .accountId(jsonObject.getString("accountId"))
                .name(jsonObject.getString("name"))
                .correctAssignments(correctAssignments)
                .incorrectAssignments(incorrectAssignments)
                .build();
    }

    public static Map<String, PersonalResultDto> toPersonalResults(JsonObject jsonObject) {
        var personalResults = new HashMap<String, PersonalResultDto>();

        jsonObject.forEach(e -> {
            var accountId = e.getKey();
            var personalResultAsJsonObject = (JsonObject) e.getValue();
            var personalResult = toPersonalResult(personalResultAsJsonObject);

            personalResults.put(accountId, personalResult);
        });

        return personalResults;
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("accountId", accountId)
                .put("name", name)
                .put("correctAssignments", AssignmentDto.toJsonArray(correctAssignments))
                .put("incorrectAssignments", AssignmentDto.toJsonArray(incorrectAssignments));
    }

    public static JsonObject toJsonObject(Map<String, PersonalResultDto> personalResults) {
        var jsonObject = new JsonObject();
        personalResults.forEach((accountId, personalResultDto) ->
                jsonObject.put(accountId, personalResultDto.toJsonObject()));

        return jsonObject;
    }
}
