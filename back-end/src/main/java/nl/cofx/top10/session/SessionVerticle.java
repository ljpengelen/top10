package nl.cofx.top10.session;

import io.jsonwebtoken.Jwts;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.InternalServerErrorException;
import nl.cofx.top10.ValidationException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static nl.cofx.top10.account.ExternalAccountVerticle.EXTERNAL_LOGIN_ADDRESS;
import static nl.cofx.top10.session.SessionConfiguration.JWT_COOKIE_NAME;
import static nl.cofx.top10.session.SessionConfiguration.SESSION_EXPIRATION_IN_SECONDS;

@Slf4j
@RequiredArgsConstructor
public class SessionVerticle extends AbstractVerticle {

    private final GoogleOauth2 googleOauth2;
    private final MicrosoftOauth2 microsoftOauth2;
    private final Router router;
    private final SecretKey secretKey;
    private final boolean useSecureCookies;

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

        var loginProvider = requestBody.getString("provider");
        var code = requestBody.getString("code");
        var externalUser = getExternalUser(loginProvider, code);

        vertx.eventBus().request(EXTERNAL_LOGIN_ADDRESS, externalUser, reply -> {
            if (reply.failed()) {
                var id = externalUser.getString("id");
                var provider = externalUser.getString("provider");
                var errorMessage = String.format("Unable to retrieve account ID for external ID \"%s\" and provider \"%s\"", id, provider);
                routingContext.fail(new InternalServerErrorException(errorMessage, reply.cause()));
            }

            var account = (JsonObject) reply.result().body();
            var jwt = Jwts.builder()
                    .expiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                    .subject(account.getString("accountId"))
                    .claim("name", account.getString("name"))
                    .claim("emailAddress", account.getString("emailAddress"))
                    .signWith(secretKey, Jwts.SIG.HS512)
                    .compact();

            var cookie = Cookie.cookie(JWT_COOKIE_NAME, jwt)
                    .setHttpOnly(true)
                    .setMaxAge(SESSION_EXPIRATION_IN_SECONDS)
                    .setPath("/")
                    .setSameSite(CookieSameSite.LAX)
                    .setSecure(useSecureCookies);

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .addCookie(cookie)
                    .end(sessionCreated(jwt, account));
        });
    }

    private JsonObject getExternalUser(String provider, String code) {
        return switch (provider) {
            case "google" -> googleOauth2.getUser(code);
            case "microsoft" -> microsoftOauth2.getUser(code);
            default -> throw new ValidationException(String.format("Invalid login provider: \"%s\"", provider));
        };
    }

    private void handleLogOut(RoutingContext routingContext) {
        log.debug("Logging out");

        var cookie = Cookie.cookie(JWT_COOKIE_NAME, "")
                .setHttpOnly(true)
                .setMaxAge(0)
                .setPath("/")
                .setSameSite(CookieSameSite.LAX)
                .setSecure(useSecureCookies);

        routingContext.response()
                .setStatusCode(204)
                .addCookie(cookie)
                .end();
    }

    private JsonObject getRequestBodyAsJson(RoutingContext routingContext) {
        try {
            return routingContext.body().asJsonObject();
        } catch (Exception e) {
            log.debug("Unable to parse request body as JSON");
            return null;
        }
    }

    private Buffer sessionCreated(String jwt, JsonObject account) {
        return new JsonObject()
                .put("status", "SESSION_CREATED")
                .put("token", jwt)
                .put("name", account.getString("name"))
                .put("emailAddress", account.getString("emailAddress"))
                .toBuffer();
    }
}
