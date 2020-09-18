package nl.friendlymirror.top10.session;

import static nl.friendlymirror.top10.session.SessionConfiguration.JWT_COOKIE_NAME;
import static nl.friendlymirror.top10.session.SessionConfiguration.SESSION_EXPIRATION_IN_SECONDS;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.jwt.Jwt;

@Log4j2
@RequiredArgsConstructor
public class SessionStatusVerticle extends AbstractVerticle {

    private static final Buffer INVALID_SESSION_RESPONSE = new JsonObject()
            .put("status", "INVALID_SESSION")
            .toBuffer();

    private static final Buffer NO_SESSION_RESPONSE = new JsonObject()
            .put("status", "NO_SESSION")
            .toBuffer();

    private final Jwt jwt;
    private final Router router;
    private final SecretKey secretKey;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/session/status").handler(this::handle);
    }

    private void handle(RoutingContext routingContext) {
        log.info("Session status");

        var response = routingContext.response();
        response.putHeader("content-type", "application/json");

        var existingCookie = routingContext.getCookie(JWT_COOKIE_NAME);
        if (existingCookie == null) {
            log.debug("No session cookie present");
            response.end(NO_SESSION_RESPONSE);
            return;
        }

        var jws = jwt.getJws(existingCookie.getValue());
        if (jws == null) {
            log.debug("Session cookie is invalid");
            response.end(INVALID_SESSION_RESPONSE);
            return;
        }

        log.debug("Extending expiration date of session cookie");

        var subject = jws.getBody().getSubject();
        var jwt = Jwts.builder()
                .setExpiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                .setSubject(subject)
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        var newCookie = Cookie.cookie(JWT_COOKIE_NAME, jwt)
                .setHttpOnly(true)
                .setMaxAge(SESSION_EXPIRATION_IN_SECONDS)
                .setPath("/");

        log.debug("Setting new session cookie");

        response.addCookie(newCookie);
        response.end(validSession(jwt));
    }

    private Buffer validSession(String token) {
        return new JsonObject()
                .put("status", "VALID_SESSION")
                .put("token", token)
                .toBuffer();
    }
}
