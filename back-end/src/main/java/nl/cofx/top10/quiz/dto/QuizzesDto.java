package nl.cofx.top10.quiz.dto;

import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import lombok.*;

@Value
@Builder(toBuilder = true)
public class QuizzesDto {

    @Singular
    List<QuizDto> quizzes;

    public JsonArray toJsonArray() {
        var jsonObjects = quizzes.stream()
                .map(QuizDto::toJsonObject)
                .collect(Collectors.toList());

        return new JsonArray(jsonObjects);
    }

    public static QuizzesDto fromJsonArray(JsonArray jsonArray) {
        var builder = QuizzesDto.builder();

        for (var i = 0; i < jsonArray.size(); ++i) {
            builder.quiz(QuizDto.fromJsonObject(jsonArray.getJsonObject(i)));
        }

        return builder.build();
    }
}
