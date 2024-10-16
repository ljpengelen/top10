package nl.cofx.top10.session;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.AbstractVerticleTest;
import nl.cofx.top10.ErrorHandlers;
import nl.cofx.top10.InvalidCredentialsException;
import nl.cofx.top10.RandomPort;
import nl.cofx.top10.http.BodyPublisher;
import nl.cofx.top10.http.JsonObjectBodyHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
class SessionVerticleTest extends AbstractVerticleTest {

    private static final String PATH = "/session/logIn";
    private static final int INTERNAL_ID = 1234;
    private static final String NAME = "John Doe";
    private static final String EMAIL_ADDRESS = "john.doe@example.com";

    private final GoogleOauth2 googleOauth2 = mock(GoogleOauth2.class);
    private final MicrosoftOauth2 microsoftOauth2 = mock(MicrosoftOauth2.class);

    private int port;

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer(RandomPort.httpServerOptions());
        var router = Router.router(vertx);

        ErrorHandlers.configure(router);
        server.requestHandler(router);

        vertx.deployVerticle(new SessionVerticle(googleOauth2, microsoftOauth2, router, SECRET_KEY, true), deploymentResult -> {
            if (deploymentResult.succeeded()) {
                server.listen().onComplete(asyncServer -> {
                    if (asyncServer.failed()) {
                        vertxTestContext.failNow(asyncServer.cause());
                        return;
                    }

                    port = asyncServer.result().actualPort();
                    log.info("Using port {}", port);
                    vertxTestContext.completeNow();
                });
            } else {
                vertxTestContext.failNow(deploymentResult.cause());
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
        assertThat(response.body().getString("error")).isEqualTo("Request body is empty");
    }

    @Test
    public void rejectsRequestWithUnknownLoginProvider() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("provider", "facebook")))
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().getString("error")).isEqualTo("Invalid login provider: \"facebook\"");
    }

    @Test
    public void rejectsRequestWithInvalidAuthorizationCode() throws IOException, InterruptedException {
        var invalidAuthorizationCode = "invalidAuthorizationCode";
        when(googleOauth2.getUser(invalidAuthorizationCode)).thenThrow(new InvalidCredentialsException("Invalid authorization code: \"invalidAuthorizationCode\""));

        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JsonObject().put("provider", "google").put("code", invalidAuthorizationCode);
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(requestBody))
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body().getString("error")).isEqualTo("Invalid authorization code: \"invalidAuthorizationCode\"");
    }

    @Test
    public void setsCookieGivenValidGoogleIdToken(Vertx vertx) throws IOException, InterruptedException {
        var validAuthorizationCode = "validAuthorizationCode";
        when(googleOauth2.getUser(validAuthorizationCode)).thenReturn(user());

        vertx.eventBus().consumer("external.login.accountId", message ->
                message.reply(new JsonObject()
                        .put("accountId", INTERNAL_ID)
                        .put("name", NAME)
                        .put("emailAddress", EMAIL_ADDRESS)));

        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JsonObject().put("provider", "google").put("code", validAuthorizationCode);
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

        var body = claims.getPayload();
        assertThat(body).isNotNull();
        assertThat(body.getSubject()).isEqualTo(String.valueOf(INTERNAL_ID));
        assertThat(body.get("name")).isEqualTo(NAME);
        assertThat(body.get("emailAddress")).isEqualTo(EMAIL_ADDRESS);
    }

    @Test
    public void returnsAccessTokenGivenValidGoogleIdToken(Vertx vertx) throws IOException, InterruptedException {
        var validAuthorizationCode = "validAuthorizationCode";
        when(googleOauth2.getUser(validAuthorizationCode)).thenReturn(user());

        vertx.eventBus().consumer("external.login.accountId", message ->
                message.reply(new JsonObject()
                        .put("accountId", INTERNAL_ID)
                        .put("name", NAME)
                        .put("emailAddress", EMAIL_ADDRESS)));

        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JsonObject().put("provider", "google").put("code", validAuthorizationCode);
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

        var body = claims.getPayload();
        assertThat(body).isNotNull();
        assertThat(body.getSubject()).isEqualTo(String.valueOf(INTERNAL_ID));
        assertThat(body.get("name")).isEqualTo(NAME);
        assertThat(body.get("emailAddress")).isEqualTo(EMAIL_ADDRESS);
    }

    private JsonObject user() {
        return new JsonObject()
                .put("name", NAME)
                .put("emailAddress", EMAIL_ADDRESS)
                .put("id", "johndoe")
                .put("provider", "google");
    }
}
