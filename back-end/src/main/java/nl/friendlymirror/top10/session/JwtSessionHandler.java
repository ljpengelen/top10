package nl.friendlymirror.top10.session;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import nl.friendlymirror.top10.jwt.Jwt;

@RequiredArgsConstructor
public class JwtSessionHandler implements Handler<RoutingContext> {

    public static final String JWT_COOKIE_NAME = "jwt";

    private static final Buffer INVALID_SESSION_RESPONSE = new JsonObject()
            .put("error", "Invalid session")
            .toBuffer();

    private final Jwt jwt;

    public void handle(RoutingContext routingContext) {
        var response = routingContext.response();
        var cookie = routingContext.getCookie(JWT_COOKIE_NAME);
        if (cookie == null) {
            response.putHeader("content-type", "application/json");
            response.end(INVALID_SESSION_RESPONSE);

            return;
        }

        Jws<Claims> sessionToken = jwt.getJws(cookie.getValue());
        if (sessionToken == null) {
            response.putHeader("content-type", "application/json");
            response.end(INVALID_SESSION_RESPONSE);

            return;
        }

        var userId = sessionToken.getBody().getSubject();
        routingContext.setUser(new JwtSessionUser(userId));

        routingContext.next();
    }
}
