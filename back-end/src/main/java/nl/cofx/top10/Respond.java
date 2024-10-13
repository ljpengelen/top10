package nl.cofx.top10;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Respond {

    private static final String CONTENT_TYPE = "content-type";
    private static final String APPLICATION_JSON = "application/json";

    void withErrorMessage(RoutingContext routingContext, int statusCode, String message) {
        routingContext.response()
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(statusCode)
                .end(new JsonObject().put("error", message).toBuffer());
    }

    void withErrorMessage(RoutingContext routingContext, int statusCode) {
        routingContext.response()
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(statusCode)
                .end();
    }
}
