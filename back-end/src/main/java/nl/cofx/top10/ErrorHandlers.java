package nl.cofx.top10;

import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ErrorHandlers {

    public static void configure(Router router) {
        router.errorHandler(404, routingContext -> {
            log.info("Handling 404");
            Respond.withErrorMessage(routingContext, 404, "Resource not found");
        });

        router.errorHandler(400, routingContext -> {
            log.info("Handling 400");
            Respond.withErrorMessage(routingContext, 400, "Bad request");
        });

        router.errorHandler(500, routingContext -> {
            log.info("Handling 500");

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
            } else if (failure instanceof ForbiddenException) {
                message = failure.getMessage();
                statusCode = 403;
            } else if (failure instanceof NotFoundException) {
                message = failure.getMessage();
                statusCode = 404;
            } else if (failure instanceof ConflictException) {
                message = failure.getMessage();
                statusCode = 409;
            } else {
                log.error(message, failure);
            }

            Respond.withErrorMessage(routingContext, statusCode, message);
        });
    }
}
