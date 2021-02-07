package nl.cofx.top10.quiz.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.*;

@Value
@Builder
public class PersonalResultDto {

    Integer accountId;
    String name;
    @Singular
    List<AssignmentDto> correctAssignments;
    @Singular
    List<AssignmentDto> incorrectAssignments;

    public static PersonalResultDto fromJsonObject(JsonObject jsonObject) {
        var correctAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("correctAssignments"));
        var incorrectAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("incorrectAssignments"));
        return PersonalResultDto.builder()
                .accountId(jsonObject.getInteger("accountId"))
                .name(jsonObject.getString("name"))
                .correctAssignments(correctAssignments)
                .incorrectAssignments(incorrectAssignments)
                .build();
    }

    public static List<PersonalResultDto> fromJsonArray(JsonArray jsonArray) {
        var personalResults = new ArrayList<PersonalResultDto>();

        for (int i = 0; i < jsonArray.size(); i++) {
            personalResults.add(fromJsonObject(jsonArray.getJsonObject(i)));
        }

        return personalResults;
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("accountId", accountId)
                .put("name", name)
                .put("numberOfCorrectAssignments", correctAssignments.size())
                .put("correctAssignments", AssignmentDto.toJsonArray(correctAssignments))
                .put("incorrectAssignments", AssignmentDto.toJsonArray(incorrectAssignments));
    }

    public static JsonArray toJsonArray(List<PersonalResultDto> personalResults) {
        return new JsonArray(personalResults.stream()
                .map(PersonalResultDto::toJsonObject)
                .collect(Collectors.toList()));
    }
}
