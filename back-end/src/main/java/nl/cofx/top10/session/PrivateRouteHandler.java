package nl.cofx.top10.session;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class PrivateRouteHandler implements Handler<RoutingContext> {

    public void handle(RoutingContext routingContext) {
        var response = routingContext.response();

        if (routingContext.user() == null) {
            invalidSession(response);
            return;
        }

        routingContext.next();
    }

    private void invalidSession(HttpServerResponse response) {
        response.putHeader("content-type", "application/json")
                .setStatusCode(401)
                .end(new JsonObject()
                        .put("error", "No authenticated user found")
                        .toBuffer());
    }
}
