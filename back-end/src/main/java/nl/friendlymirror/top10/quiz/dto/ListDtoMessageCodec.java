package nl.friendlymirror.top10.quiz.dto;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class ListDtoMessageCodec implements MessageCodec<ListDto, ListDto> {

    @Override
    public void encodeToWire(Buffer buffer, ListDto listDto) {
        var jsonObject = listDto.toJsonObject();
        var string = jsonObject.encode();
        var length = string.getBytes().length;

        buffer.appendInt(length);
        buffer.appendString(string);
    }

    @Override
    public ListDto decodeFromWire(int pos, Buffer buffer) {
        var length = buffer.getInt(pos);
        var string = buffer.getString(pos + 4, pos + 4 + length);
        var jsonObject = new JsonObject(string);

        return ListDto.fromJsonObject(jsonObject);
    }

    @Override
    public ListDto transform(ListDto listDto) {
        return listDto;
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
