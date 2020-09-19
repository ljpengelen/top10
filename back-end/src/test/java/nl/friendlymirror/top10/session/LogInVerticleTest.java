package nl.friendlymirror.top10.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.security.GeneralSecurityException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.AbstractVerticleTest;
import nl.friendlymirror.top10.RandomPort;
import nl.friendlymirror.top10.http.BodyPublisher;
import nl.friendlymirror.top10.http.JsonObjectBodyHandler;

@Log4j2
class LogInVerticleTest extends AbstractVerticleTest {

    private static final String PATH = "/session/logIn";
    private static final int INTERNAL_ID = 1234;

    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);

    private final int port = RandomPort.get();

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
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
    public void rejectsRequestWithoutBody() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("No credentials provided");
    }

    @Test
    public void rejectsRequestWithUnknownLoginType() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("type", "FACEBOOK")))
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Unknown login type");
    }

    @Test
    public void rejectsRequestWithUnverifiableToken() throws GeneralSecurityException, IOException, InterruptedException {
        var unverifiableTokenString = "unverifiableTokenString";
        when(googleIdTokenVerifier.verify(unverifiableTokenString)).thenThrow(new RuntimeException());

        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", unverifiableTokenString);
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(requestBody))
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body().getString("error")).isEqualTo("Invalid credentials");
    }

    @Test
    public void rejectsRequestWithInvalidToken() throws GeneralSecurityException, IOException, InterruptedException {
        var invalidTokenString = "invalidTokenString";
        when(googleIdTokenVerifier.verify(invalidTokenString)).thenReturn(null);

        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", invalidTokenString);
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(requestBody))
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body().getString("error")).isEqualTo("Invalid credentials");
    }

    @Test
    public void setsCookieGivenValidGoogleIdToken(Vertx vertx) throws GeneralSecurityException, IOException, InterruptedException {
        var googleIdToken = mock(GoogleIdToken.class);
        var payload = mock(GoogleIdToken.Payload.class);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("johndoe");
        when(payload.getEmail()).thenReturn("john.doe@example.com");
        when(payload.get("name")).thenReturn("John Doe");

        var validTokenString = "validTokenString";
        when(googleIdTokenVerifier.verify(validTokenString)).thenReturn(googleIdToken);

        vertx.eventBus().consumer("google.login.accountId", message -> message.reply(INTERNAL_ID));

        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", validTokenString);
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(requestBody))
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);

        var optionalCookie = response.headers().firstValue("Set-Cookie");
        assertThat(optionalCookie).isNotEmpty();
        var cookieValue = extractCookie("jwt", optionalCookie.get());
        var claims = jwt.getJws(cookieValue);
        assertThat(claims).isNotNull();

        var body = claims.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSubject()).isEqualTo(String.valueOf(INTERNAL_ID));
    }

    @Test
    public void returnsAccessTokenGivenValidGoogleIdToken(Vertx vertx) throws GeneralSecurityException, IOException, InterruptedException {
        var googleIdToken = mock(GoogleIdToken.class);
        var payload = mock(GoogleIdToken.Payload.class);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getSubject()).thenReturn("johndoe");
        when(payload.getEmail()).thenReturn("john.doe@example.com");
        when(payload.get("name")).thenReturn("John Doe");

        var validTokenString = "validTokenString";
        when(googleIdTokenVerifier.verify(validTokenString)).thenReturn(googleIdToken);

        vertx.eventBus().consumer("google.login.accountId", message -> message.reply(INTERNAL_ID));

        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JsonObject().put("type", "GOOGLE").put("token", validTokenString);
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(requestBody))
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);

        var token = response.body().getString("token");
        assertThat(token).isNotBlank();
        var claims = jwt.getJws(token);
        assertThat(claims).isNotNull();

        var body = claims.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSubject()).isEqualTo(String.valueOf(INTERNAL_ID));
    }
}
