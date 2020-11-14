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
public class PersonalResultDto {

    Integer accountId;
    Integer numberOfCorrectAssignments;
    List<AssignmentDto> correctAssignments;
    List<AssignmentDto> incorrectAssignments;

    public static PersonalResultDto fromJsonObject(JsonObject jsonObject) {
        var correctAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("correctAssignments"));
        var incorrectAssignments = AssignmentDto.fromJsonArray(jsonObject.getJsonArray("incorrectAssignments"));
        return PersonalResultDto.builder()
                .accountId(jsonObject.getInteger("accountId"))
                .numberOfCorrectAssignments(correctAssignments.size())
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
                .put("numberOfCorrectAssignments", numberOfCorrectAssignments)
                .put("correctAssignments", AssignmentDto.toJsonArray(correctAssignments))
                .put("incorrectAssignments", AssignmentDto.toJsonArray(incorrectAssignments));
    }

    public static JsonArray toJsonArray(List<PersonalResultDto> personalResults) {
        return new JsonArray(personalResults.stream()
                .map(PersonalResultDto::toJsonObject)
                .collect(Collectors.toList()));
    }
}
