package nl.cofx.top10.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.cofx.top10.Application;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.http.*;

@ExtendWith(VertxExtension.class)
public class SessionIntegrationTest {

    private static final String CSRF_TOKEN_HEADER_NAME = "x-csrf-token";

    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final TestConfig config = new TestConfig();

    @BeforeEach
    public void setCookieHandler() {
        CookieHandler.setDefault(new CookieManager());
    }

    @BeforeEach
    public void cleanUp() throws SQLException {
        var connection = DriverManager.getConnection(config.getJdbcUrl(), config.getJdbcUsername(), config.getJdbcPassword());
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();
    }

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        var application = new Application(config, googleIdTokenVerifier, vertx);
        application.start().onComplete(vertxTestContext.completing());
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
        var allQuizzesRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/quiz"))
                .build();
        var allQuizzesResponse = httpClient.send(allQuizzesRequest, new JsonObjectBodyHandler());

        assertThat(allQuizzesResponse.statusCode()).isEqualTo(400);
        assertThat(allQuizzesResponse.body().getString("error")).isEqualTo("Missing authorization header");

        allQuizzesRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/quiz"))
                .header("Authorization", "Bearer invalidAccessToken")
                .build();
        allQuizzesResponse = httpClient.send(allQuizzesRequest, new JsonObjectBodyHandler());

        assertThat(allQuizzesResponse.statusCode()).isEqualTo(401);
        assertThat(allQuizzesResponse.body().getString("error")).isEqualTo("No session");

        var getStatusRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/status"))
                .header("Origin", config.getCsrfTarget())
                .build();
        var getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        var optionalCsrfToken = getStatusResponse.headers().firstValue(CSRF_TOKEN_HEADER_NAME);
        assertThat(optionalCsrfToken).isNotEmpty();
        var csrfToken = optionalCsrfToken.get();

        var logInRequest = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject()
                        .put("type", "GOOGLE")
                        .put("token", validGoogleIdToken)))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/logIn"))
                .header("Origin", config.getCsrfTarget())
                .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
                .build();
        var logInResponse = httpClient.send(logInRequest, new JsonObjectBodyHandler());

        assertThat(logInResponse.statusCode()).isEqualTo(200);
        assertThat(logInResponse.body().getString("status")).isEqualTo("SESSION_CREATED");
        var accessToken = logInResponse.body().getString("token");
        assertThat(accessToken).isNotBlank();

        getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        assertThat(getStatusResponse.body().getString("status")).isEqualTo("VALID_SESSION");

        allQuizzesRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/quiz"))
                .header("Authorization", "Bearer " + accessToken)
                .build();
        var successfulAllQuizzesResponse = httpClient.send(allQuizzesRequest, new JsonArrayBodyHandler());

        assertThat(successfulAllQuizzesResponse.statusCode()).isEqualTo(200);
    }

    @Test
    public void handlesLogout() throws GeneralSecurityException, IOException, InterruptedException {
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
        var getStatusRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/status"))
                .header("Origin", config.getCsrfTarget())
                .build();
        var getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        var optionalCsrfToken = getStatusResponse.headers().firstValue(CSRF_TOKEN_HEADER_NAME);
        assertThat(optionalCsrfToken).isNotEmpty();
        var csrfToken = optionalCsrfToken.get();

        var logInRequest = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject()
                        .put("type", "GOOGLE")
                        .put("token", validGoogleIdToken)))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/logIn"))
                .header("Origin", config.getCsrfTarget())
                .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
                .build();
        var logInResponse = httpClient.send(logInRequest, new JsonObjectBodyHandler());

        assertThat(logInResponse.statusCode()).isEqualTo(200);
        assertThat(logInResponse.body().getString("status")).isEqualTo("SESSION_CREATED");
        var accessToken = logInResponse.body().getString("token");
        assertThat(accessToken).isNotBlank();

        getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());
        assertThat(getStatusResponse.body().getString("status")).isEqualTo("VALID_SESSION");

        optionalCsrfToken = getStatusResponse.headers().firstValue(CSRF_TOKEN_HEADER_NAME);
        assertThat(optionalCsrfToken).isNotEmpty();
        csrfToken = optionalCsrfToken.get();

        var logOutRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/logOut"))
                .header("Origin", config.getCsrfTarget())
                .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
                .build();
        var logOutResponse = httpClient.send(logOutRequest, HttpResponse.BodyHandlers.discarding());

        assertThat(logOutResponse.statusCode()).isEqualTo(204);

        getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());
        assertThat(getStatusResponse.body().getString("status")).isEqualTo("NO_SESSION");
    }
}
