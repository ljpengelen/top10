package nl.friendlymirror.top10.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.Application;
import nl.friendlymirror.top10.config.TestConfig;

@Log4j2
@ExtendWith(VertxExtension.class)
public class SessionIntegrationTest {

    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final TestConfig config = new TestConfig();

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        var application = new Application(config, googleIdTokenVerifier, vertx);
        application.start().setHandler(vertxTestContext.completing());
    }

    @Test
    public void rejectsRequestWithoutOrigin(Vertx vertx, VertxTestContext vertxTestContext) {
        var webClient = WebClient.create(vertx);
        webClient.get(config.getHttpPort(), "localhost", "/session/status")
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to session-status endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        var response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(400);
                        assertThat(response.bodyAsJsonObject().getString("error")).isEqualTo("Origin and referer do not match \"http://localhost:8080\"");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void returnsNoSessionGivenNoSession(Vertx vertx, VertxTestContext vertxTestContext) {
        var webClient = WebClient.create(vertx);
        webClient.get(config.getHttpPort(), "localhost", "/session/status")
                .putHeader("Origin", config.getCsrfTarget())
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to session-status endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        var response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.bodyAsJsonObject().getString("status")).isEqualTo("NO_SESSION");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void handlesLogin(Vertx vertx, VertxTestContext vertxTestContext) throws GeneralSecurityException, IOException {
        var payload = mock(GoogleIdToken.Payload.class);
        when(payload.getSubject()).thenReturn("googleId");
        when(payload.getEmail()).thenReturn("jane.doe@example.org");
        when(payload.get("name")).thenReturn("Jane Doe");
        var googleIdToken = mock(GoogleIdToken.class);
        when(googleIdToken.getPayload()).thenReturn(payload);
        var validGoogleIdToken = "validGoogleIdToken";
        when(googleIdTokenVerifier.verify(validGoogleIdToken)).thenReturn(googleIdToken);

        var webClient = WebClient.create(vertx);
        var webClientSession = WebClientSession.create(webClient);
        webClientSession.get(config.getHttpPort(), "localhost", "/session/status")
                .putHeader("Origin", config.getCsrfTarget())
                .send(getStatus -> {
                    if (getStatus.failed()) {
                        var cause = getStatus.cause();
                        log.error("Request to session-status endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        var csrfToken = getStatus.result().getHeader("X-CSRF-Token");
                        assertThat(csrfToken).isNotBlank();
                    });

                    var csrfToken = getStatus.result().getHeader("X-CSRF-Token");
                    webClientSession.post(config.getHttpPort(), "localhost", "/session/logIn")
                            .putHeader("Origin", config.getCsrfTarget())
                            .putHeader("X-CSRF-Token", csrfToken)
                            .sendJsonObject(new JsonObject()
                                    .put("type", "GOOGLE")
                                    .put("token", validGoogleIdToken), login -> {
                                if (login.failed()) {
                                    var cause = login.cause();
                                    log.error("Request to login endpoint failed", cause);
                                    vertxTestContext.failNow(cause);
                                }

                                vertxTestContext.verify(() -> {
                                    var loginResult = login.result();
                                    assertThat(loginResult.statusCode()).isEqualTo(200);
                                    var loginBody = loginResult.bodyAsJsonObject();
                                    assertThat(loginBody.getString("status")).isEqualTo("SESSION_CREATED");
                                    assertThat(loginBody.getString("token")).isNotBlank();
                                });
                                vertxTestContext.completeNow();
                            });
                });
    }
}
