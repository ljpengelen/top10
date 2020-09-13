package nl.friendlymirror.top10.session;

import static nl.friendlymirror.top10.account.GoogleAccountVerticle.GOOGLE_LOGIN_ADDRESS;
import static nl.friendlymirror.top10.session.SessionConfiguration.JWT_COOKIE_NAME;
import static nl.friendlymirror.top10.session.SessionConfiguration.SESSION_EXPIRATION_IN_SECONDS;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import javax.crypto.SecretKey;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

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

    private static final Buffer BAD_REQUEST_RESPONSE = new JsonObject()
            .put("error", "Bad request")
            .toBuffer();
    private static final Buffer INVALID_CREDENTIALS_RESPONSE = new JsonObject()
            .put("error", "Invalid credentials")
            .toBuffer();
    private static final Buffer INTERNAL_SERVER_ERROR_RESPONSE = new JsonObject()
            .put("error", "Internal server error")
            .toBuffer();

    private final String googleOauth2ClientId;
    private final Router router;
    private final SecretKey secretKey;

    private JsonFactory jsonFactory = new JacksonFactory();
    private HttpTransport httpTransport;
    private GoogleIdTokenVerifier verifier;

    private void handle(RoutingContext routingContext) {
        var response = routingContext.response().putHeader("content-type", "application/json");

        var requestBody = getRequestBodyAsJson(routingContext);
        if (requestBody == null) {
            badRequest(response);
            return;
        }

        var loginType = requestBody.getString("type");
        if (!"GOOGLE".equals(loginType)) {
            badRequest(response);
            return;
        }

        var googleUserData = getGoogleUserData(requestBody.getString("token"));
        if (googleUserData == null) {
            response.setStatusCode(401)
                    .end(INVALID_CREDENTIALS_RESPONSE);
        }

        vertx.eventBus().request(GOOGLE_LOGIN_ADDRESS, googleUserData, reply -> {
            if (reply.failed()) {
                log.error("Unable to retrieve account ID for Google ID \"{}\"", googleUserData.getString("id"), reply.cause());
                response.setStatusCode(500)
                        .end(INTERNAL_SERVER_ERROR_RESPONSE);
                return;
            }

            var accountId = (String) reply.result().body();
            var jwt = Jwts.builder()
                    .setExpiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                    .setSubject(accountId)
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
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                var payload = idToken.getPayload();
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

    private void badRequest(HttpServerResponse response) {
        response.setStatusCode(400)
                .end(BAD_REQUEST_RESPONSE);
    }

    private Buffer sessionCreated(String jwt) {
        return new JsonObject()
                .put("status", "SESSION_CREATED")
                .put("token", jwt)
                .toBuffer();
    }

    @Override
    public void start() throws Exception {
        log.info("Starting");

        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        verifier = new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
                .setAudience(Collections.singletonList(googleOauth2ClientId))
                .build();

        router.route(HttpMethod.POST, "/session/logIn").handler(BodyHandler.create());
        router.route(HttpMethod.POST, "/session/logIn").handler(this::handle);
    }
}
