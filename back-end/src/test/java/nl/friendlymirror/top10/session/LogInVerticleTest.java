package nl.friendlymirror.top10.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.jwt.Jwt;

@Log4j2
@ExtendWith(VertxExtension.class)
class LogInVerticleTest {

    private static final String PATH = "/session/logIn";

    private static final String ENCODED_SECRET_KEY = "FsJtRGG84NM7BNewGo5AXvg6GJ1DKedDJjkirpDEAOtVgdi6j3f+THdeEika6v3dB8N4DO0fywkd+JK2A5eKLQ==";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ENCODED_SECRET_KEY));
    private final Jwt jwt = new Jwt(SECRET_KEY);

    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);

    private int port;

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) throws IOException {
        var socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

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
}
