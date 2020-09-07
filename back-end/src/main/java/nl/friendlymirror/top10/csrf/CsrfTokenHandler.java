package nl.friendlymirror.top10.csrf;

import java.util.Set;

import javax.crypto.SecretKey;

import org.apache.commons.math3.random.RandomDataGenerator;

import io.jsonwebtoken.*;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import nl.friendlymirror.top10.jwt.Jwt;

@RequiredArgsConstructor
public class CsrfTokenHandler implements Handler<RoutingContext> {

    private static final Set<HttpMethod> METHODS_TO_IGNORE = Set.of(HttpMethod.GET, HttpMethod.OPTIONS);

    private static final Buffer INVALID_CSRF_TOKEN_RESPONSE = new JsonObject()
            .put("error", "Invalid CSRF token")
            .toBuffer();

    private static final String CSRF_TOKEN_HEADER_NAME = "X-CSRF-Token";
    private static final String CSRF_TOKEN_COOKIE_NAME = "__Host-csrf-token";
    private static final String CSRF_TOKEN_CLAIM_NAME = "csrfToken";

    private final RandomDataGenerator randomDataGenerator = new RandomDataGenerator();

    private final Jwt jwt;
    private final SecretKey secretKey;

    @Override
    public void handle(RoutingContext routingContext) {
        var request = routingContext.request();
        if (!METHODS_TO_IGNORE.contains(request.method()) && !hasValidCsrfToken(request)) {
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(INVALID_CSRF_TOKEN_RESPONSE);

            return;
        }

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

        return tokenInJws.equals(tokenInHeader);
    }

    private String generateToken() {
        return randomDataGenerator.nextSecureHexString(24);
    }

    private void setCsrfTokens(HttpServerResponse response) {
        var token = generateToken();
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
