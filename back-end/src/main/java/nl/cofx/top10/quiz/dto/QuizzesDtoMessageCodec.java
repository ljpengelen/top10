package nl.cofx.top10.quiz.dto;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonArray;

public class QuizzesDtoMessageCodec implements MessageCodec<QuizzesDto, QuizzesDto> {

    @Override
    public void encodeToWire(Buffer buffer, QuizzesDto quizzesDto) {
        var jsonArray = quizzesDto.toJsonArray();
        var string = jsonArray.encode();
        var length = string.getBytes().length;

        buffer.appendInt(length);
        buffer.appendString(string);
    }

    @Override
    public QuizzesDto decodeFromWire(int pos, Buffer buffer) {
        var length = buffer.getInt(pos);
        var string = buffer.getString(pos + 4, pos + 4 + length);
        var jsonArray = new JsonArray(string);

        return QuizzesDto.fromJsonArray(jsonArray);
    }

    @Override
    public QuizzesDto transform(QuizzesDto quizzesDto) {
        return quizzesDto;
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
