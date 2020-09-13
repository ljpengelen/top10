package nl.friendlymirror.top10.session;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import nl.friendlymirror.top10.jwt.Jwt;

@RequiredArgsConstructor
public class JwtSessionHandler implements Handler<RoutingContext> {

    private static final Buffer INVALID_SESSION_RESPONSE = new JsonObject()
            .put("error", "Invalid session")
            .toBuffer();

    private final Jwt jwt;

    public void handle(RoutingContext routingContext) {
        var response = routingContext.response();

        var token = routingContext.request().getHeader("Authorization");
        if (token == null) {
            invalidSession(response);
            return;
        }

        if (!token.startsWith("Bearer ") || token.length() < 8) {
            invalidSession(response);
            return;
        }

        Jws<Claims> claims = jwt.getJws(token.substring(7));
        if (claims == null) {
            invalidSession(response);
            return;
        }

        var userId = claims.getBody().getSubject();
        routingContext.setUser(new JwtSessionUser(userId));

        routingContext.next();
    }

    private void invalidSession(HttpServerResponse response) {
        response.putHeader("content-type", "application/json")
                .setStatusCode(401)
                .end(INVALID_SESSION_RESPONSE);
    }
}
