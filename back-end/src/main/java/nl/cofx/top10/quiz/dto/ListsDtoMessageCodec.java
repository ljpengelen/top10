package nl.cofx.top10.quiz.dto;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonArray;

public class ListsDtoMessageCodec implements MessageCodec<ListsDto, ListsDto> {

    @Override
    public void encodeToWire(Buffer buffer, ListsDto listsDto) {
        var jsonArray = listsDto.toJsonArray();
        var string = jsonArray.encode();
        var length = string.getBytes().length;

        buffer.appendInt(length);
        buffer.appendString(string);
    }

    @Override
    public ListsDto decodeFromWire(int pos, Buffer buffer) {
        var length = buffer.getInt(pos);
        var string = buffer.getString(pos + 4, pos + 4 + length);
        var jsonArray = new JsonArray(string);

        return ListsDto.fromJsonArray(jsonArray);
    }

    @Override
    public ListsDto transform(ListsDto listsDto) {
        return listsDto;
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
