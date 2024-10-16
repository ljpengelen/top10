package nl.cofx.top10;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FailureHandler {

    private static void handleFailure(RoutingContext routingContext) {
        log.info("Handling failure with throwable {} and status {}",
                routingContext.failure().toString(), routingContext.statusCode());
        routingContext.next();
    }

    public static void configure(Router router) {
        router.route().failureHandler(FailureHandler::handleFailure);
    }
}
