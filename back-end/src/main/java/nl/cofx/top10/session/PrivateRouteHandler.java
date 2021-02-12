package nl.cofx.top10.session;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class PrivateRouteHandler implements Handler<RoutingContext> {

    public void handle(RoutingContext routingContext) {
        if (routingContext.user() == null) {
            routingContext.response().putHeader("content-type", "application/json")
                    .setStatusCode(401)
                    .end(new JsonObject()
                            .put("error", "No authenticated user found")
                            .toBuffer());
            return;
        }

        routingContext.next();
    }
}
