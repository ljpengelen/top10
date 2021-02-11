package nl.cofx.top10.session;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.jwt.Jwt;

@Slf4j
@RequiredArgsConstructor
public class JwtSessionHandler implements Handler<RoutingContext> {

    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    private final Jwt jwt;

    public void handle(RoutingContext routingContext) {
        var token = routingContext.request().getHeader(AUTHORIZATION_HEADER_NAME);
        if (token == null) {
            log.debug("Missing authorization header");
            routingContext.next();
            return;
        }

        if (!token.startsWith("Bearer ") || token.length() < 8) {
            log.debug("Malformed authorization header \"{}\"", token);
            routingContext.next();
            return;
        }

        var claims = jwt.getJws(token.substring(7));
        if (claims == null) {
            log.debug("Unable to parse claims \"{}\"", token);
            routingContext.next();
            return;
        }

        var accountId = Integer.parseInt(claims.getBody().getSubject());
        routingContext.setUser(User.create(new JsonObject().put("accountId", accountId)));

        routingContext.next();
    }
}
