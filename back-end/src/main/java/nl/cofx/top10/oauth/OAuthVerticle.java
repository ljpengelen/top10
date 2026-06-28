package nl.cofx.top10.oauth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.ForbiddenException;
import nl.cofx.top10.InternalServerErrorException;
import nl.cofx.top10.ValidationException;
import nl.cofx.top10.jwt.Jwt;
import nl.cofx.top10.random.TokenGenerator;
import nl.cofx.top10.session.GoogleOauth2;
import nl.cofx.top10.session.MicrosoftOauth2;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static nl.cofx.top10.account.ExternalAccountVerticle.EXTERNAL_LOGIN_ADDRESS;
import static nl.cofx.top10.session.SessionConfiguration.JWT_COOKIE_NAME;
import static nl.cofx.top10.session.SessionConfiguration.SESSION_EXPIRATION_IN_SECONDS;

@Slf4j
@RequiredArgsConstructor
public class OAuthVerticle extends AbstractVerticle {

    private static final Map<String, OAuthState> serverStateToOAuthState = new ConcurrentHashMap<>();
    private static final Map<String, OAuthState> codeToOAuthState = new ConcurrentHashMap<>();
    private static final MessageDigest SHA_256_INSTANCE = sha256Instance();

    private final GoogleOauth2 googleOauth2;
    private final String homeUrl;
    private final Jwt jwt;
    private final MicrosoftOauth2 microsoftOauth2;
    private final Router router;
    private final SecretKey secretKey;
    private final boolean useSecureCookies;

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/oauth/authorize").handler(this::handleAuthorize);
        router.route(HttpMethod.GET, "/oauth/log-in/:provider").handler(this::handleLogIn);
        router.route(HttpMethod.POST, "/oauth/token")
                .handler(BodyHandler.create())
                .handler(this::handleToken);
        router.route(HttpMethod.POST, "/oauth/log-out").handler(this::handleLogOut);
    }

    private void handleAuthorize(RoutingContext routingContext) {
        log.debug("Authenticating");

        var request = routingContext.request();
        var jws = jws(request);

        if (jws != null) {
            handleValidSessionCookie(routingContext, jws, request);
            return;
        }

        authenticate(routingContext);
    }

    private void handleValidSessionCookie(RoutingContext routingContext, Jws<Claims> jws, HttpServerRequest request) {
        log.debug("Extending expiration date of existing session cookie");

        var body = jws.getPayload();
        var subject = body.getSubject();
        var name = body.get("name", String.class);
        var emailAddress = body.get("emailAddress", String.class);
        var provider = body.get("provider", String.class);
        var jwt = Jwts.builder()
                .expiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                .subject(subject)
                .claim("name", name)
                .claim("emailAddress", emailAddress)
                .claim("provider", provider)
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();

        var codeChallenge = request.getParam("code_challenge");
        var clientRedirectUrl = request.getParam("redirect_url");
        var clientState = request.getParam("state");

        var code = TokenGenerator.generateToken();
        codeToOAuthState.put(code, OAuthState.builder()
                .codeChallenge(codeChallenge)
                .redirectUrl(clientRedirectUrl)
                .state(clientState)
                .token(jwt)
                .build());

        var newCookie = Cookie.cookie(JWT_COOKIE_NAME, jwt)
                .setHttpOnly(true)
                .setMaxAge(SESSION_EXPIRATION_IN_SECONDS)
                .setPath("/")
                .setSameSite(CookieSameSite.LAX)
                .setSecure(useSecureCookies);

        routingContext.response().addCookie(newCookie);

        var redirectUrl = request.getParam("redirect_url");
        var state = request.getParam("state");
        redirectUrl += "?state=" + state + "&code=" + code;

        routingContext.redirect(redirectUrl);
    }

    private void authenticate(RoutingContext routingContext) {
        log.debug("Authenticating with third party");

        var request = routingContext.request();

        var provider = request.getParam("provider");
        var codeChallenge = request.getParam("code_challenge");
        var clientRedirectUrl = request.getParam("redirect_url");
        var clientState = request.getParam("state");

        var serverState = TokenGenerator.generateToken();
        serverStateToOAuthState.put(serverState, OAuthState.builder()
                .codeChallenge(codeChallenge)
                .redirectUrl(clientRedirectUrl)
                .state(clientState)
                .build());

        redirectToLoginForm(routingContext, provider, serverState);
    }

    private void redirectToLoginForm(RoutingContext routingContext, String provider, String state) {
        if (provider == null) throw invalidProviderException(null);

        switch (provider) {
            case "google" -> googleOauth2.redirectToLoginForm(routingContext, state);
            case "microsoft" -> microsoftOauth2.redirectToLoginForm(routingContext, state);
            default -> throw invalidProviderException(provider);
        }
    }

    private void handleLogIn(RoutingContext routingContext) {
        log.debug("Logging in");

        var request = routingContext.request();
        var serverState = request.getParam("state");

        var clientState = serverStateToOAuthState.get(serverState);
        if (clientState == null) {
            log.debug("No client state found for server state {}", serverState);
            routingContext.redirect(homeUrl);
            return;
        }

        var provider = routingContext.pathParam("provider");
        if (provider == null) throw invalidProviderException(null);

        var code = routingContext.queryParams().get("code");
        if (code == null) {
            log.debug("No code provided by provider {}", provider);
            routingContext.redirect(clientState.getRedirectUrl() + "?error=error");
            return;
        }

        var externalUser = getExternalUser(provider, code);

        vertx.eventBus().request(EXTERNAL_LOGIN_ADDRESS, externalUser, reply -> {
            if (reply.failed()) {
                var id = externalUser.getString("id");
                var errorMessage = String.format("Unable to retrieve account ID for external ID \"%s\" and provider \"%s\"", id, provider);
                routingContext.fail(new InternalServerErrorException(errorMessage, reply.cause()));
            }

            var account = (JsonObject) reply.result().body();
            var jwt = Jwts.builder()
                    .expiration(Date.from(Instant.now().plusSeconds(SESSION_EXPIRATION_IN_SECONDS)))
                    .subject(account.getString("accountId"))
                    .claim("name", account.getString("name"))
                    .claim("emailAddress", account.getString("emailAddress"))
                    .claim("provider", provider)
                    .signWith(secretKey, Jwts.SIG.HS512)
                    .compact();

            var newCode = TokenGenerator.generateToken();
            codeToOAuthState.put(newCode, OAuthState.builder()
                    .codeChallenge(clientState.getCodeChallenge())
                    .redirectUrl(clientState.getRedirectUrl())
                    .token(jwt)
                    .build());

            var redirectUrl = clientState.getRedirectUrl() +
                    "?state=" + clientState.getState() +
                    "&code=" + newCode;
            routingContext.redirect(redirectUrl);
        });
    }

    private Jws<Claims> jws(HttpServerRequest request) {
        var existingCookie = request.getCookie(JWT_COOKIE_NAME);
        if (existingCookie == null) {
            log.debug("No session cookie present");
            return null;
        }

        var jws = jwt.getJws(existingCookie.getValue());
        if (jws == null) {
            log.debug("Session cookie is invalid");
            return null;
        }

        var actualProvider = jws.getPayload().get("provider", String.class);
        var expectedProvider = request.getParam("provider");
        if (expectedProvider != null && !expectedProvider.equals(actualProvider)) {
            log.debug("Session cookie has provider {} instead of {}", actualProvider, expectedProvider);
            return null;
        }

        return jws;
    }

    private JsonObject getExternalUser(String provider, String code) {
        return switch (provider) {
            case "google" -> googleOauth2.getUser(code);
            case "microsoft" -> microsoftOauth2.getUser(code);
            default -> throw invalidProviderException(provider);
        };
    }

    private static ValidationException invalidProviderException(String provider) {
        return new ValidationException(String.format("Invalid login provider: \"%s\"", provider));
    }

    private void handleToken(RoutingContext routingContext) {
        log.debug("Handling token request");

        var body = routingContext.body().asJsonObject();
        var code = body.getString("code");
        var codeVerifier = body.getString("codeVerifier");
        var redirectUrl = body.getString("redirectUrl");

        var oauthState = codeToOAuthState.get(code);
        if (!Objects.equals(redirectUrl, oauthState.getRedirectUrl()))
            throw new ForbiddenException("Invalid redirect URL");

        var codeChallenge = sha256(codeVerifier);
        if (!Objects.equals(codeChallenge, oauthState.getCodeChallenge()))
            throw new ForbiddenException("Invalid code verifier");

        var token = oauthState.getToken();

        var cookie = Cookie.cookie(JWT_COOKIE_NAME, token)
                .setHttpOnly(true)
                .setMaxAge(SESSION_EXPIRATION_IN_SECONDS)
                .setPath("/")
                .setSameSite(CookieSameSite.LAX)
                .setSecure(useSecureCookies);

        routingContext.response()
                .putHeader("content-type", "application/json")
                .addCookie(cookie)
                .end(new JsonObject().put("token", token).toBuffer());
    }

    @SneakyThrows
    private static MessageDigest sha256Instance() {
        return MessageDigest.getInstance("SHA-256");
    }

    private static String sha256(String input) {
        var digest = SHA_256_INSTANCE.digest(input.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(digest);
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
                .addCookie(cookie)
                .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                .end();
    }
}
