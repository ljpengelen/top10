package nl.cofx.top10;

import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class FailureHandler {

    private static void handleFailure(RoutingContext routingContext) {
        var route = routingContext.currentRoute();
        var statusCode = routingContext.statusCode();
        var failure = routingContext.failure();
        log.error("Unexpected failure when handling route for path {} and methods {} resulted in status code {}",
                route.getPath(), route.methods(), statusCode, failure);
        if (failure != null) {
            Respond.withErrorMessage(routingContext, 500, "Internal server error");
            return;
        }

        Respond.withErrorMessage(routingContext, statusCode);
    }

    public static void add(List<Route> routes) {
        for (Route route : routes) {
            route.failureHandler(FailureHandler::handleFailure);
        }
    }
}
