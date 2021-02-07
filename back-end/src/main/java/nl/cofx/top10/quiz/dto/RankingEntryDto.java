package nl.cofx.top10.quiz.dto;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RankingEntryDto {

    int rank;
    String externalAccountId;
    String name;
    int numberOfCorrectAssignments;

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("rank", rank)
                .put("externalAccountId", externalAccountId)
                .put("name", name)
                .put("numberOfCorrectAssignments", numberOfCorrectAssignments);
    }
}
