package nl.friendlymirror.top10.session.csrf;

import java.util.Set;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.jwt.Jwt;
import nl.friendlymirror.top10.random.TokenGenerator;

@Log4j2
@RequiredArgsConstructor
public class CsrfTokenHandler implements Handler<RoutingContext> {

    public static final String CSRF_TOKEN_HEADER_NAME = "x-csrf-token";

    private static final Set<HttpMethod> METHODS_TO_IGNORE = Set.of(HttpMethod.GET, HttpMethod.OPTIONS);

    private static final Buffer INVALID_CSRF_TOKEN_RESPONSE = new JsonObject()
            .put("error", "Invalid CSRF token")
            .toBuffer();

    private static final String CSRF_TOKEN_COOKIE_NAME = "csrf-token";
    private static final String CSRF_TOKEN_CLAIM_NAME = "csrfToken";

    private final Jwt jwt;
    private final SecretKey secretKey;

    @Override
    public void handle(RoutingContext routingContext) {
        log.debug("Validating CSRF token");

        var request = routingContext.request();
        if (!METHODS_TO_IGNORE.contains(request.method()) && !hasValidCsrfToken(request)) {
            log.debug("Invalid CSRF token");

            routingContext.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(INVALID_CSRF_TOKEN_RESPONSE);

            return;
        }

        log.debug("CSRF token not required or valid CSRF token");

        setCsrfTokens(routingContext.response());

        routingContext.next();
    }

    private boolean hasValidCsrfToken(HttpServerRequest request) {
        var cookie = request.getCookie(CSRF_TOKEN_COOKIE_NAME);
        if (cookie == null) {
            return false;
        }

        var csrfJws = jwt.getJws(cookie.getValue());
        if (csrfJws == null) {
            return false;
        }

        var tokenInJws = csrfJws.getBody().get(CSRF_TOKEN_CLAIM_NAME, String.class);
        var tokenInHeader = request.getHeader(CSRF_TOKEN_HEADER_NAME);

        if (tokenInJws == null || tokenInHeader == null) {
            return false;
        }

        return tokenInJws.equals(tokenInHeader);
    }

    private void setCsrfTokens(HttpServerResponse response) {
        log.debug("Setting CSRF token in header and cookie");

        var token = TokenGenerator.generateToken();
        response.putHeader(CSRF_TOKEN_HEADER_NAME, token);

        var jwt = Jwts.builder()
                .claim(CSRF_TOKEN_CLAIM_NAME, token)
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        var cookie = Cookie.cookie(CSRF_TOKEN_COOKIE_NAME, jwt)
                .setHttpOnly(true)
                .setPath("/");
        response.addCookie(cookie);
    }
}
