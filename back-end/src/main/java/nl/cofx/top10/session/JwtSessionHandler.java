package nl.cofx.top10.session;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import nl.cofx.top10.jwt.Jwt;

@RequiredArgsConstructor
public class JwtSessionHandler implements Handler<RoutingContext> {

    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    private final Jwt jwt;

    public void handle(RoutingContext routingContext) {
        var response = routingContext.response();

        var token = routingContext.request().getHeader(AUTHORIZATION_HEADER_NAME);
        if (token == null) {
            badRequest(response, "Missing authorization header");
            return;
        }

        if (!token.startsWith("Bearer ") || token.length() < 8) {
            badRequest(response, "Malformed authorization header");
            return;
        }

        var claims = jwt.getJws(token.substring(7));
        if (claims == null) {
            invalidSession(response);
            return;
        }

        var accountId = Integer.parseInt(claims.getBody().getSubject());
        routingContext.setUser(User.create(new JsonObject().put("accountId", accountId)));

        routingContext.next();
    }

    private void badRequest(HttpServerResponse response, String errorMessage) {
        response.putHeader("content-type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject()
                        .put("error", errorMessage)
                        .toBuffer());
    }

    private void invalidSession(HttpServerResponse response) {
        response.putHeader("content-type", "application/json")
                .setStatusCode(401)
                .end(new JsonObject()
                        .put("error", "No session")
                        .toBuffer());
    }
}
