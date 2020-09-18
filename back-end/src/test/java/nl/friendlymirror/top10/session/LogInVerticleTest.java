package nl.friendlymirror.top10.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.AbstractVerticleTest;
import nl.friendlymirror.top10.RandomPort;

@Log4j2
class LogInVerticleTest extends AbstractVerticleTest {

    private static final String PATH = "/session/logIn";

    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);

    private final int port = RandomPort.get();

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) throws IOException {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);
        vertx.deployVerticle(new LogInVerticle(googleIdTokenVerifier, router, SECRET_KEY), deploymentResult -> {
            if (deploymentResult.succeeded()) {
                server.listen(port, vertxTestContext.completing());
            } else {
                var cause = deploymentResult.cause();
                log.error("Failed to deploy verticle", cause);
                vertxTestContext.failNow(cause);
            }
        });
    }

    @Test
    public void rejectsRequestWithoutBody(Vertx vertx, VertxTestContext vertxTestContext) {
        var webClient = WebClient.create(vertx);
        webClient.post(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to log-in endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(400);
                        assertThat(response.bodyAsJsonObject().getString("error")).isEqualTo("No credentials provided");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void rejectsRequestWithUnknownLoginType(Vertx vertx, VertxTestContext vertxTestContext) {
        var webClient = WebClient.create(vertx);
        var requestBody = new JsonObject().put("type", "FACEBOOK");
        webClient.post(port, "localhost", PATH)
                .sendJsonObject(requestBody, ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to log-in endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(400);
                        assertThat(response.bodyAsJsonObject().getString("error")).isEqualTo("Unknown login type");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void rejectsRequestWithUnverifiableToken(Vertx vertx, VertxTestContext vertxTestContext) throws GeneralSecurityException, IOException {
        var unverifiableTokenString = "unverifiableTokenString";
        when(googleIdTokenVerifier.verify(unverifiableTokenString)).thenThrow(new RuntimeException());

        var webClient = WebClient.create(vertx);
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", unverifiableTokenString);
        webClient.post(port, "localhost", PATH)
                .sendJsonObject(requestBody, ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to log-in endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(401);
                        assertThat(response.bodyAsJsonObject().getString("error")).isEqualTo("Invalid credentials");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void rejectsRequestWithInvalidToken(Vertx vertx, VertxTestContext vertxTestContext) throws GeneralSecurityException, IOException {
        var invalidTokenString = "invalidTokenString";
        when(googleIdTokenVerifier.verify(invalidTokenString)).thenReturn(null);

        var webClient = WebClient.create(vertx);
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", invalidTokenString);
        webClient.post(port, "localhost", PATH)
                .sendJsonObject(requestBody, ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to log-in endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(401);
                        assertThat(response.bodyAsJsonObject().getString("error")).isEqualTo("Invalid credentials");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void setsCookieGivenValidGoogleIdToken(Vertx vertx, VertxTestContext vertxTestContext) throws GeneralSecurityException, IOException {
        var googleIdToken = mock(GoogleIdToken.class);
        var payload = mock(GoogleIdToken.Payload.class);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("johndoe");
        when(payload.getEmail()).thenReturn("john.doe@example.com");
        when(payload.get("name")).thenReturn("John Doe");

        var validTokenString = "validTokenString";
        when(googleIdTokenVerifier.verify(validTokenString)).thenReturn(googleIdToken);

        var internalId = "internalIdForJohnDoe";
        vertx.eventBus().consumer("google.login.accountId", message -> message.reply(internalId));

        var webClient = WebClient.create(vertx);
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", validTokenString);
        webClient.post(port, "localhost", PATH)
                .sendJsonObject(requestBody, ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to log-in endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(200);

                        var cookieValue = extractCookie("jwt", response.cookies());
                        var claims = jwt.getJws(cookieValue);
                        assertThat(claims).isNotNull();

                        var body = claims.getBody();
                        assertThat(body).isNotNull();
                        assertThat(body.getSubject()).isEqualTo(internalId);
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void returnsAccessTokenGivenValidGoogleIdToken(Vertx vertx, VertxTestContext vertxTestContext) throws GeneralSecurityException, IOException {
        var googleIdToken = mock(GoogleIdToken.class);
        var payload = mock(GoogleIdToken.Payload.class);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("johndoe");
        when(payload.getEmail()).thenReturn("john.doe@example.com");
        when(payload.get("name")).thenReturn("John Doe");

        var validTokenString = "validTokenString";
        when(googleIdTokenVerifier.verify(validTokenString)).thenReturn(googleIdToken);

        var internalId = "internalIdForJohnDoe";
        vertx.eventBus().consumer("google.login.accountId", message -> message.reply(internalId));

        var webClient = WebClient.create(vertx);
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", validTokenString);
        webClient.post(port, "localhost", PATH)
                .sendJsonObject(requestBody, ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to log-in endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(200);

                        var token = response.bodyAsJsonObject().getString("token");
                        assertThat(token).isNotBlank();

                        var claims = jwt.getJws(token);
                        assertThat(claims.getBody().getSubject()).isEqualTo(internalId);
                    });
                    vertxTestContext.completeNow();
                });
    }
}
