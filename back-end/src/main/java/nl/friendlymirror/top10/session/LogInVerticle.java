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
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class LogInVerticle extends AbstractVerticle {

    private static final Buffer INVALID_CREDENTIALS_RESPONSE = new JsonObject()
            .put("error", "Invalid credentials")
            .toBuffer();
    private static final Buffer INTERNAL_SERVER_ERROR_RESPONSE = new JsonObject()
            .put("error", "Internal server error")
            .toBuffer();

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final Router router;
    private final SecretKey secretKey;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.POST, "/session/logIn").handler(BodyHandler.create());
        router.route(HttpMethod.POST, "/session/logIn").handler(this::handle);
    }

    private void handle(RoutingContext routingContext) {
        log.debug("Logging in");

        var response = routingContext.response().putHeader("content-type", "application/json");

        var requestBody = getRequestBodyAsJson(routingContext);
        if (requestBody == null) {
            log.debug("No request body provided");
            badRequest(response, "No credentials provided");
            return;
        }

        var loginType = requestBody.getString("type");
        if (!"GOOGLE".equals(loginType)) {
            log.debug("Invalid login type \"{}\"", loginType);
            badRequest(response, "Unknown login type");
            return;
        }

        var googleUserData = getGoogleUserData(requestBody.getString("token"));
        if (googleUserData == null) {
            response.setStatusCode(401)
                    .end(INVALID_CREDENTIALS_RESPONSE);
            return;
        }

        vertx.eventBus().request(GOOGLE_LOGIN_ADDRESS, googleUserData, reply -> {
            if (reply.failed()) {
                log.error("Unable to retrieve account ID for Google ID \"{}\"", googleUserData.getString("id"), reply.cause());
                response.setStatusCode(500)
                        .end(INTERNAL_SERVER_ERROR_RESPONSE);
                return;
            }

            var accountId = (int) reply.result().body();
            log.debug("Retrieved account ID \"{}\" for Google ID", accountId);
            var jwt = Jwts.builder()
                    .setExpiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                    .setSubject(String.valueOf(accountId))
                    .signWith(secretKey, SignatureAlgorithm.HS512)
                    .compact();

            var cookie = Cookie.cookie(JWT_COOKIE_NAME, jwt)
                    .setHttpOnly(true)
                    .setMaxAge(SESSION_EXPIRATION_IN_SECONDS)
                    .setPath("/");

            response.addCookie(cookie)
                    .end(sessionCreated(jwt));
        });
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
                log.warn("Invalid Google ID token: \"{}\"", idTokenString);
            }
        } catch (Exception e) {
            log.warn("Unable to verify Google ID token \"{}\"", idTokenString, e);
        }

        return null;
    }

    private JsonObject getRequestBodyAsJson(RoutingContext routingContext) {
        try {
            return routingContext.getBodyAsJson();
        } catch (Exception e) {
            log.warn("Unable to parse request body as JSON", e);
            return null;
        }
    }

    private void badRequest(HttpServerResponse response, String errorMessage) {
        response.setStatusCode(400)
                .end(new JsonObject()
                        .put("error", errorMessage)
                        .toBuffer());
    }

    private Buffer sessionCreated(String jwt) {
        return new JsonObject()
                .put("status", "SESSION_CREATED")
                .put("token", jwt)
                .toBuffer();
    }
}
