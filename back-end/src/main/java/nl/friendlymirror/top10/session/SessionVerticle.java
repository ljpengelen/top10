package nl.friendlymirror.top10.session;

import static nl.friendlymirror.top10.account.GoogleAccountVerticle.GOOGLE_LOGIN_ADDRESS;
import static nl.friendlymirror.top10.session.SessionConfiguration.JWT_COOKIE_NAME;
import static nl.friendlymirror.top10.session.SessionConfiguration.SESSION_EXPIRATION_IN_SECONDS;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.*;

@Log4j2
@RequiredArgsConstructor
public class SessionVerticle extends AbstractVerticle {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final Router router;
    private final SecretKey secretKey;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.POST, "/session/logIn").handler(BodyHandler.create());
        router.route(HttpMethod.POST, "/session/logIn").handler(this::handleLogIn);
        router.route(HttpMethod.POST, "/session/logOut").handler(this::handleLogOut);
    }

    private void handleLogIn(RoutingContext routingContext) {
        log.debug("Logging in");

        var requestBody = getRequestBodyAsJson(routingContext);
        if (requestBody == null) {
            throw new ValidationException("Request body is empty");
        }

        var loginType = requestBody.getString("type");
        if (!"GOOGLE".equals(loginType)) {
            throw new ValidationException(String.format("Invalid login type \"%s\"", loginType));
        }

        var googleUserData = getGoogleUserData(requestBody.getString("token"));
        vertx.eventBus().request(GOOGLE_LOGIN_ADDRESS, googleUserData, reply -> {
            if (reply.failed()) {
                var errorMessage = String.format("Unable to retrieve account ID for Google ID \"%s\"", googleUserData.getString("id"));
                routingContext.fail(new InternalServerErrorException(errorMessage, reply.cause()));
            }

            var accountId = (int) reply.result().body();
            var jwt = Jwts.builder()
                    .setExpiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                    .setSubject(String.valueOf(accountId))
                    .signWith(secretKey, SignatureAlgorithm.HS512)
                    .compact();

            var cookie = Cookie.cookie(JWT_COOKIE_NAME, jwt)
                    .setHttpOnly(true)
                    .setMaxAge(SESSION_EXPIRATION_IN_SECONDS)
                    .setPath("/");

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .addCookie(cookie)
                    .end(sessionCreated(jwt));
        });
    }

    private void handleLogOut(RoutingContext routingContext) {
        log.debug("Logging out");

        var cookie = Cookie.cookie(JWT_COOKIE_NAME, "")
                .setHttpOnly(true)
                .setMaxAge(0)
                .setPath("/");

        routingContext.response()
                .setStatusCode(201)
                .addCookie(cookie)
                .end();
    }

    private JsonObject getGoogleUserData(String idTokenString) {
        log.debug("Verifying Google ID token");

        try {
            var googleIdToken = googleIdTokenVerifier.verify(idTokenString);
            if (googleIdToken != null) {
                log.debug("Valid Google ID token");
                var payload = googleIdToken.getPayload();
                return new JsonObject()
                        .put("name", payload.get("name"))
                        .put("emailAddress", payload.getEmail())
                        .put("id", payload.getSubject());
            } else {
                throw new InvalidCredentialsException(String.format("Invalid Google ID token: \"%s\"", idTokenString));
            }
        } catch (Exception e) {
            log.warn("Exception occurred while validating Google ID token", e);
            throw new InvalidCredentialsException(String.format("Unable to verify Google ID token \"%s\"", idTokenString));
        }
    }

    private JsonObject getRequestBodyAsJson(RoutingContext routingContext) {
        try {
            return routingContext.getBodyAsJson();
        } catch (Exception e) {
            log.debug("Unable to parse request body as JSON");
            return null;
        }
    }

    private Buffer sessionCreated(String jwt) {
        return new JsonObject()
                .put("status", "SESSION_CREATED")
                .put("token", jwt)
                .toBuffer();
    }
}
