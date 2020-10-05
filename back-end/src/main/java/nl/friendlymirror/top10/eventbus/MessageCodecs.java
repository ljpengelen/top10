package nl.friendlymirror.top10.eventbus;

import io.vertx.core.eventbus.EventBus;
import nl.friendlymirror.top10.quiz.dto.*;

public class MessageCodecs {

    public static void register(EventBus eventBus) {
        eventBus.registerDefaultCodec(ListDto.class, new ListDtoMessageCodec());
        eventBus.registerDefaultCodec(ListsDto.class, new ListsDtoMessageCodec());
    }
}
