package nl.cofx.top10.quiz.dto;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class QuizDtoMessageCodec implements MessageCodec<QuizDto, QuizDto> {

    @Override
    public void encodeToWire(Buffer buffer, QuizDto quizDto) {
        var jsonObject = quizDto.toJsonObject();
        var string = jsonObject.encode();
        var length = string.getBytes().length;

        buffer.appendInt(length);
        buffer.appendString(string);
    }

    @Override
    public QuizDto decodeFromWire(int pos, Buffer buffer) {
        var length = buffer.getInt(pos);
        var string = buffer.getString(pos + 4, pos + 4 + length);
        var jsonObject = new JsonObject(string);

        return QuizDto.fromJsonObject(jsonObject);
    }

    @Override
    public QuizDto transform(QuizDto quizDto) {
        return quizDto;
    }

    @Override
    public String name() {
        return this.getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
