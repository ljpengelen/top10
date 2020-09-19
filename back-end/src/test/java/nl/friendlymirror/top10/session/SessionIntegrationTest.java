package nl.friendlymirror.top10.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.security.GeneralSecurityException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.friendlymirror.top10.Application;
import nl.friendlymirror.top10.config.TestConfig;

@ExtendWith(VertxExtension.class)
public class SessionIntegrationTest {

    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final TestConfig config = new TestConfig();

    @BeforeEach
    public void setCookieHandler() {
        CookieHandler.setDefault(new CookieManager());
    }

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        var application = new Application(config, googleIdTokenVerifier, vertx);
        application.start().onComplete(vertxTestContext.completing());
    }

    private static class JsonObjectBodyHandler implements HttpResponse.BodyHandler<JsonObject> {

        @Override
        public HttpResponse.BodySubscriber<JsonObject> apply(HttpResponse.ResponseInfo responseInfo) {
            return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), body -> new JsonObject(Buffer.buffer(body)));
        }
    }

    private HttpRequest.BodyPublisher ofJsonObject(JsonObject body) {
        return HttpRequest.BodyPublishers.ofString(body.toString());
    }

    @Test
    public void rejectsRequestWithoutOrigin() throws IOException, InterruptedException {
        var httpClient = HttpClient.newBuilder()
                .cookieHandler(CookieHandler.getDefault())
                .build();
        var getStatusRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/status"))
                .build();
        var getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        assertThat(getStatusResponse.statusCode()).isEqualTo(400);
        assertThat(getStatusResponse.body().getString("error")).isEqualTo("Origin and referer do not match \"http://localhost:8080\"");
    }

    @Test
    public void returnsNoSessionGivenNoSession() throws IOException, InterruptedException {
        var httpClient = HttpClient.newBuilder()
                .cookieHandler(CookieHandler.getDefault())
                .build();
        var getStatusRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/status"))
                .header("Origin", config.getCsrfTarget())
                .build();
        var getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        assertThat(getStatusResponse.statusCode()).isEqualTo(200);
        assertThat(getStatusResponse.body().getString("status")).isEqualTo("NO_SESSION");
    }

    @Test
    public void handlesLogin() throws GeneralSecurityException, IOException, InterruptedException {
        var payload = mock(GoogleIdToken.Payload.class);
        when(payload.getSubject()).thenReturn("googleId");
        when(payload.getEmail()).thenReturn("jane.doe@example.org");
        when(payload.get("name")).thenReturn("Jane Doe");
        var googleIdToken = mock(GoogleIdToken.class);
        when(googleIdToken.getPayload()).thenReturn(payload);
        var validGoogleIdToken = "validGoogleIdToken";
        when(googleIdTokenVerifier.verify(validGoogleIdToken)).thenReturn(googleIdToken);

        var httpClient = HttpClient.newBuilder()
                .cookieHandler(CookieHandler.getDefault())
                .build();
        var echoBody = new JsonObject()
                .put("someKey", "someValue");
        var echoRequest = HttpRequest.newBuilder()
                .POST(ofJsonObject(echoBody))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/echo"))
                .build();
        var echoResponse = httpClient.send(echoRequest, new JsonObjectBodyHandler());

        assertThat(echoResponse.statusCode()).isEqualTo(400);
        assertThat(echoResponse.body().getString("error")).isEqualTo("Missing authorization header");

        echoRequest = HttpRequest.newBuilder()
                .POST(ofJsonObject(echoBody))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/echo"))
                .header("Authorization", "Bearer invalidAccessToken")
                .build();
        echoResponse = httpClient.send(echoRequest, new JsonObjectBodyHandler());

        assertThat(echoResponse.statusCode()).isEqualTo(401);
        assertThat(echoResponse.body().getString("error")).isEqualTo("No session");

        var getStatusRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/status"))
                .header("Origin", config.getCsrfTarget())
                .build();
        var getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        var optionalCsrfToken = getStatusResponse.headers().firstValue("X-CSRF-Token");
        assertThat(optionalCsrfToken).isNotEmpty();
        var csrfToken = optionalCsrfToken.get();

        var logInRequest = HttpRequest.newBuilder()
                .POST(ofJsonObject(new JsonObject()
                        .put("type", "GOOGLE")
                        .put("token", validGoogleIdToken)))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/logIn"))
                .header("Origin", config.getCsrfTarget())
                .header("X-CSRF-Token", csrfToken)
                .build();
        var logInResponse = httpClient.send(logInRequest, new JsonObjectBodyHandler());

        assertThat(logInResponse.statusCode()).isEqualTo(200);
        assertThat(logInResponse.body().getString("status")).isEqualTo("SESSION_CREATED");
        var accessToken = logInResponse.body().getString("token");
        assertThat(accessToken).isNotBlank();

        getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        assertThat(getStatusResponse.body().getString("status")).isEqualTo("VALID_SESSION");

        echoRequest = HttpRequest.newBuilder()
                .POST(ofJsonObject(echoBody))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/echo"))
                .header("Authorization", "Bearer " + accessToken)
                .build();
        echoResponse = httpClient.send(echoRequest, new JsonObjectBodyHandler());

        assertThat(echoResponse.statusCode()).isEqualTo(200);
        assertThat(echoResponse.body()).isEqualTo(echoBody);
    }
}
