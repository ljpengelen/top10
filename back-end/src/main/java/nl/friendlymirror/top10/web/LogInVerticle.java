package nl.friendlymirror.top10.web;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.session.JwtSessionHandler;

@Log4j2
@RequiredArgsConstructor
public class LogInVerticle extends AbstractVerticle {

    private static final long SESSION_EXPIRATION_IN_SECONDS = 30 * 60;

    private final Router router;
    private final SecretKey secretKey;

    private void handle(RoutingContext routingContext) {
        log.info("Login");

        var jwt = Jwts.builder()
                .setExpiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                .setSubject("abcd")
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        var cookie = Cookie.cookie(JwtSessionHandler.JWT_COOKIE_NAME, jwt)
                .setHttpOnly(true)
                .setMaxAge(SESSION_EXPIRATION_IN_SECONDS)
                .setPath("/");

        routingContext.response().addCookie(cookie);

        routingContext.next();
    }

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/").handler(this::handle);
    }
}
