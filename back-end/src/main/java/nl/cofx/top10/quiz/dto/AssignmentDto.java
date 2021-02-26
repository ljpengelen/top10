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

    String listId;
    String assigneeId;
    String assigneeName;
    String creatorId;
    String creatorName;

    public static AssignmentDto fromJsonObject(JsonObject jsonObject) {
        return AssignmentDto.builder()
                .listId(jsonObject.getString("listId"))
                .assigneeId(jsonObject.getString("assigneeId"))
                .assigneeName(jsonObject.getString("assigneeName"))
                .creatorId(jsonObject.getString("creatorId"))
                .creatorName(jsonObject.getString("creatorName"))
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
                .put("assigneeName", assigneeName)
                .put("creatorId", creatorId)
                .put("creatorName", creatorName);
    }

    public static JsonArray toJsonArray(List<AssignmentDto> assignments) {
        return new JsonArray(assignments.stream()
                .map(AssignmentDto::toJsonObject)
                .collect(Collectors.toList()));
    }
}
