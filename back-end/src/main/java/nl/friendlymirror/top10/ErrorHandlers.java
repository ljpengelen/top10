package nl.friendlymirror.top10;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ErrorHandlers {

    public static void configure(Router router) {
        router.errorHandler(404, routingContext ->
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(404)
                        .end(new JsonObject().put("error", "Resource not found").toBuffer()));

        router.errorHandler(500, routingContext -> {
            var failure = routingContext.failure();

            var message = "Internal server error";
            var statusCode = 500;

            if (failure instanceof InternalServerErrorException) {
                message = failure.getMessage();
                log.error(message, failure);
            } else if (failure instanceof InvalidCredentialsException) {
                message = failure.getMessage();
                statusCode = 401;
            } else if (failure instanceof ValidationException) {
                message = failure.getMessage();
                statusCode = 400;
            } else {
                log.error(message, failure);
            }

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(statusCode)
                    .end(new JsonObject().put("error", message).toBuffer());
        });
    }
}
