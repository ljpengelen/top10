package nl.cofx.top10;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Respond {

    private static final String CONTENT_TYPE = "content-type";
    private static final String APPLICATION_JSON = "application/json";

    void withErrorMessage(RoutingContext routingContext, int statusCode, String message) {
        var response = routingContext.response();
        if (!response.headWritten())
            response.putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setStatusCode(statusCode);

        if (!response.ended())
            response.end(new JsonObject().put("error", message).toBuffer());
    }

    void withErrorMessage(RoutingContext routingContext, int statusCode) {
        var response = routingContext.response();
        if (!response.headWritten())
            response.putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setStatusCode(statusCode);

        if (!response.ended())
            response.end();
    }
}
