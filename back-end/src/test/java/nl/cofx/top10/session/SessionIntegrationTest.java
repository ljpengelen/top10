package nl.cofx.top10.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.*;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.SneakyThrows;
import nl.cofx.top10.Application;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.http.*;

@ExtendWith(VertxExtension.class)
public class SessionIntegrationTest {

    private static final String CSRF_TOKEN_HEADER_NAME = "x-csrf-token";
    private static final String NAME = "Jane Doe";
    private static final String EMAIL_ADDRESS = "jane.doe@example.org";

    private final GoogleOauth2 googleOauth2 = mock(GoogleOauth2.class);
    private final MicrosoftOauth2 microsoftOauth2 = mock(MicrosoftOauth2.class);
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
        var application = new Application(config, googleOauth2, microsoftOauth2, vertx);
        application.start().onComplete(vertxTestContext.succeedingThenComplete());
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
    public void handlesLogin() throws IOException, InterruptedException {
        var validAuthorizationCode = "validGoogleAuthorizationCode";
        when(googleOauth2.getUser(validAuthorizationCode)).thenReturn(user());

        var httpClient = HttpClient.newBuilder()
                .cookieHandler(CookieHandler.getDefault())
                .build();
        var allQuizzesRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/quiz"))
                .build();
        var allQuizzesResponse = httpClient.send(allQuizzesRequest, new JsonObjectBodyHandler());

        assertThat(allQuizzesResponse.statusCode()).isEqualTo(401);
        assertThat(allQuizzesResponse.body().getString("error")).isEqualTo("No authenticated user found");

        allQuizzesRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/quiz"))
                .header("Authorization", "Bearer invalidAccessToken")
                .build();
        allQuizzesResponse = httpClient.send(allQuizzesRequest, new JsonObjectBodyHandler());

        assertThat(allQuizzesResponse.statusCode()).isEqualTo(401);
        assertThat(allQuizzesResponse.body().getString("error")).isEqualTo("No authenticated user found");

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
                        .put("provider", "google")
                        .put("code", validAuthorizationCode)))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/logIn"))
                .header("Origin", config.getCsrfTarget())
                .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
                .build();
        var logInResponse = httpClient.send(logInRequest, new JsonObjectBodyHandler());

        assertThat(logInResponse.statusCode()).isEqualTo(200);
        var body = logInResponse.body();
        assertThat(body.getString("status")).isEqualTo("SESSION_CREATED");
        var accessToken = body.getString("token");
        assertThat(accessToken).isNotBlank();
        assertThat(body.getString("name")).isEqualTo(NAME);
        assertThat(body.getString("emailAddress")).isEqualTo(EMAIL_ADDRESS);

        getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        body = getStatusResponse.body();
        assertThat(body.getString("status")).isEqualTo("VALID_SESSION");
        assertThat(body.getString("name")).isEqualTo(NAME);
        assertThat(body.getString("emailAddress")).isEqualTo(EMAIL_ADDRESS);

        allQuizzesRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/private/quiz"))
                .header("Authorization", "Bearer " + accessToken)
                .build();
        var successfulAllQuizzesResponse = httpClient.send(allQuizzesRequest, new JsonArrayBodyHandler());

        assertThat(successfulAllQuizzesResponse.statusCode()).isEqualTo(200);
    }

    @Test
    public void handlesLogout() throws IOException, InterruptedException {
        var validAuthorizationCode = "validAuthorizationCode";
        when(googleOauth2.getUser(validAuthorizationCode)).thenReturn(user());

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
                        .put("provider", "google")
                        .put("code", validAuthorizationCode)))
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

    @Test
    public void updatesStatistics() throws IOException, InterruptedException {
        var validAuthorizationCode = "validGoogleAuthorizationCode";
        when(googleOauth2.getUser(validAuthorizationCode)).thenReturn(user());

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
                        .put("provider", "google")
                        .put("code", validAuthorizationCode)))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/logIn"))
                .header("Origin", config.getCsrfTarget())
                .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
                .build();
        var logInResponse = httpClient.send(logInRequest, new JsonObjectBodyHandler());

        assertThat(logInResponse.statusCode()).isEqualTo(200);
        var body = logInResponse.body();
        assertThat(body.getString("status")).isEqualTo("SESSION_CREATED");

        validateStatistics(EMAIL_ADDRESS, 1);

        getStatusRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/status"))
                .header("Origin", config.getCsrfTarget())
                .build();
        getStatusResponse = httpClient.send(getStatusRequest, new JsonObjectBodyHandler());

        optionalCsrfToken = getStatusResponse.headers().firstValue(CSRF_TOKEN_HEADER_NAME);
        assertThat(optionalCsrfToken).isNotEmpty();
        csrfToken = optionalCsrfToken.get();

        logInRequest = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject()
                        .put("provider", "google")
                        .put("code", validAuthorizationCode)))
                .uri(URI.create("http://localhost:" + config.getHttpPort() + "/session/logIn"))
                .header("Origin", config.getCsrfTarget())
                .header(CSRF_TOKEN_HEADER_NAME, csrfToken)
                .build();
        logInResponse = httpClient.send(logInRequest, new JsonObjectBodyHandler());

        assertThat(logInResponse.statusCode()).isEqualTo(200);
        body = logInResponse.body();
        assertThat(body.getString("status")).isEqualTo("SESSION_CREATED");

        validateStatistics(EMAIL_ADDRESS, 2);
    }

    @SneakyThrows
    private void validateStatistics(String emailAddress, int numberOfLogins) {
        var connection = DriverManager.getConnection(config.getJdbcUrl(), config.getJdbcUsername(), config.getJdbcPassword());
        var statement = connection.prepareStatement("SELECT number_of_logins FROM account WHERE email_address = ?");
        statement.setString(1, emailAddress);
        var result = statement.executeQuery();
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(numberOfLogins);
    }

    private JsonObject user() {
        return new JsonObject()
                .put("name", NAME)
                .put("emailAddress", EMAIL_ADDRESS)
                .put("id", "johndoe")
                .put("provider", "google");
    }
}
