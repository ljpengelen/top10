package nl.cofx.top10;

import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static nl.cofx.top10.ErrorHandlers.respondWithErrorMessage;

@Slf4j
public class FailureHandler {

    private static void handleFailure(Route route, RoutingContext routingContext) {
        log.error("Unexpected error when handling route for path {} and methods {}",
                route.getPath(), route.methods(), routingContext.failure());
        respondWithErrorMessage(routingContext, 500, "Internal server error");
    }

    public static void add(List<Route> routes) {
        for (Route route : routes) {
            route.failureHandler(routingContext -> handleFailure(route, routingContext));
        }
    }
}
