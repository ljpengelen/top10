package nl.cofx.top10.quiz.dto;

import java.util.*;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AssignmentDto {

    Integer listId;
    Integer assigneeId;
    Integer creatorId;

    public static AssignmentDto fromJsonObject(JsonObject jsonObject) {
        return AssignmentDto.builder()
                .listId(jsonObject.getInteger("listId"))
                .assigneeId(jsonObject.getInteger("assigneeId"))
                .creatorId(jsonObject.getInteger("creatorId"))
                .build();
    }

    public static List<AssignmentDto> fromJsonArray(JsonArray jsonArray) {
        if (jsonArray == null) {
            return Collections.emptyList();
        }

        var assignments = new ArrayList<AssignmentDto>();

        for (int i = 0; i < jsonArray.size(); i++) {
            assignments.add(fromJsonObject(jsonArray.getJsonObject(i)));
        }

        return assignments;
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("listId", listId)
                .put("assigneeId", assigneeId)
                .put("creatorId", creatorId);
    }

    public static JsonArray toJsonArray(List<AssignmentDto> assignments) {
        return new JsonArray(assignments.stream()
                .map(AssignmentDto::toJsonObject)
                .collect(Collectors.toList()));
    }
}