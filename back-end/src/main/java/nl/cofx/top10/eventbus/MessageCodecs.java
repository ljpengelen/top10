package nl.cofx.top10.eventbus;

import io.vertx.core.eventbus.EventBus;
import nl.cofx.top10.quiz.dto.*;

public class MessageCodecs {

    public static void register(EventBus eventBus) {
        eventBus.registerDefaultCodec(ListDto.class, new ListDtoMessageCodec());
        eventBus.registerDefaultCodec(ListsDto.class, new ListsDtoMessageCodec());
        eventBus.registerDefaultCodec(ResultSummaryDto.class, new ResultSummaryDtoMessageCodec());
        eventBus.registerDefaultCodec(QuizDto.class, new QuizDtoMessageCodec());
        eventBus.registerDefaultCodec(QuizzesDto.class, new QuizzesDtoMessageCodec());
    }
}
