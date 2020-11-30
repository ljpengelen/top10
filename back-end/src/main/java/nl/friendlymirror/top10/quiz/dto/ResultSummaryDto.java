package nl.friendlymirror.top10.quiz.dto;

import java.util.List;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResultSummaryDto {

    String quizId;
    List<PersonalResultDto> personalResults;

    public static ResultSummaryDto fromJsonObject(JsonObject jsonObject) {
        var personalResults = PersonalResultDto.fromJsonArray(jsonObject.getJsonArray("personalResults"));
        return ResultSummaryDto.builder()
                .quizId(jsonObject.getString("quizId"))
                .personalResults(personalResults)
                .build();
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("quizId", quizId)
                .put("personalResults", PersonalResultDto.toJsonArray(personalResults));
    }
}
