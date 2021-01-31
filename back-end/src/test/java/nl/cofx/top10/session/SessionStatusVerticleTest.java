package nl.cofx.top10.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.AbstractVerticleTest;
import nl.cofx.top10.RandomPort;
import nl.cofx.top10.http.JsonObjectBodyHandler;

@Log4j2
class SessionStatusVerticleTest extends AbstractVerticleTest {

    private static final String PATH = "/session/status";
    private static final String USER_ID = "userId";
    private static final String COOKIE_NAME = "jwt";
    private static final int FIVE_SECONDS_IN_MILLISECONDS = 5000;

    private final int port = RandomPort.get();

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        server.requestHandler(router);
        vertx.deployVerticle(new SessionStatusVerticle(jwt, router, SECRET_KEY), deploymentResult -> {
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
    public void returnsNoSessionWithoutSessionCookie() throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getString("status")).isEqualTo("NO_SESSION");
    }

    @Test
    public void returnsNoSessionGivenInvalidSessionCookie() throws IOException, InterruptedException {
        var jwt = Jwts.builder()
                .setExpiration(Date.from(Instant.now().minusSeconds(1)))
                .setSubject(USER_ID)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .header("Cookie", COOKIE_NAME + "=" + jwt)
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getString("status")).isEqualTo("INVALID_SESSION");
    }

    @Test
    public void returnsAccessTokenGivenSessionCookie() throws IOException, InterruptedException {
        var token = Jwts.builder()
                .setSubject(USER_ID)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .header("Cookie", COOKIE_NAME + "=" + token)
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getString("status")).isEqualTo("VALID_SESSION");
        assertThat(response.body().getString("token")).isNotBlank();
    }

    @Test
    public void extendsSessionCookie() throws IOException, InterruptedException {
        var token = Jwts.builder()
                .setSubject(USER_ID)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + PATH))
                .header("Cookie", COOKIE_NAME + "=" + token)
                .build();
        var response = httpClient.send(request, new JsonObjectBodyHandler());

        var optionalCookie = response.headers().firstValue("Set-Cookie");
        assertThat(optionalCookie).isNotEmpty();
        var cookieValue = extractCookie(COOKIE_NAME, optionalCookie.get());

        var claims = jwt.getJws(cookieValue);
        assertThat(claims).isNotNull();

        var body = claims.getBody();
        assertThat(body.getSubject()).isEqualTo(USER_ID);

        var eightHoursFromNow = Date.from(Instant.now().plus(8, ChronoUnit.HOURS));
        assertThat(body.getExpiration()).isCloseTo(eightHoursFromNow, FIVE_SECONDS_IN_MILLISECONDS);
    }
}
