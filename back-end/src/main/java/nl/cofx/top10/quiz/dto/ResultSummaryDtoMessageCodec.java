package nl.cofx.top10.quiz.dto;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class ResultSummaryDtoMessageCodec implements MessageCodec<ResultSummaryDto, ResultSummaryDto> {

    @Override
    public void encodeToWire(Buffer buffer, ResultSummaryDto resultSummaryDto) {
        var jsonObject = resultSummaryDto.toJsonObject();
        var string = jsonObject.encode();
        var length = string.getBytes().length;

        buffer.appendInt(length);
        buffer.appendString(string);
    }

    @Override
    public ResultSummaryDto decodeFromWire(int pos, Buffer buffer) {
        var length = buffer.getInt(pos);
        var string = buffer.getString(pos + 4, pos + 4 + length);
        var jsonObject = new JsonObject(string);

        return ResultSummaryDto.fromJsonObject(jsonObject);
    }

    @Override
    public ResultSummaryDto transform(ResultSummaryDto resultSummaryDto) {
        return resultSummaryDto;
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
