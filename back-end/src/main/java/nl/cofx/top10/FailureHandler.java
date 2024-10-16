package nl.cofx.top10;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FailureHandler {

    private static void handleFailure(RoutingContext routingContext) {
        var route = routingContext.currentRoute();
        var path = route.getPath();
        var methods = route.methods();

        var failure = routingContext.failure();

        if (failure != null) {
            log.error("Unexpected failure when handling route for path {} and methods {}",
                    path, methods, failure);
            Respond.withErrorMessage(routingContext, 500, "Internal server error");
            return;
        }

        var request = routingContext.request();
        request.body(asyncBuffer -> {
            if (asyncBuffer.failed()) {
                log.error("Unable to retrieve body of failed request", asyncBuffer.cause());
                return;
            }

            var headers = request.headers();
            var uri = request.absoluteURI();
            var body = asyncBuffer.result().toString();
            log.error("Failed request has uri {}, headers {}, and body {}", uri, headers, body);
        });

        var statusCode = routingContext.statusCode();
        log.error("Unexpected failure when handling route for path {} and methods {} resulted in status {}",
                path, methods, statusCode);

        Respond.withErrorMessage(routingContext, statusCode);
    }

    public static void configure(Router router) {
        router.route().failureHandler(FailureHandler::handleFailure);
    }
}
