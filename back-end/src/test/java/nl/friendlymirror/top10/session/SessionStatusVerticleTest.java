package nl.friendlymirror.top10.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.*;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.AbstractVerticleTest;

@Log4j2
class SessionStatusVerticleTest extends AbstractVerticleTest {

    private static final String PATH = "/session/status";
    private static final String USER_ID = "userId";
    private static final String COOKIE_NAME = "jwt";
    private static final int FIVE_SECONDS_IN_MILLISECONDS = 5000;

    private int port;

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) throws IOException {
        var socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

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
    public void returnsNoSessionWithoutSessionCookie(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to session-status endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.bodyAsJsonObject().getString("status")).isEqualTo("NO_SESSION");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void returnsNoSessionGivenInvalidSessionCookie(Vertx vertx, VertxTestContext vertxTestContext) {
        var webClient = WebClient.create(vertx);
        var webClientSession = WebClientSession.create(webClient);

        var jwt = Jwts.builder()
                .setExpiration(Date.from(Instant.now().minusSeconds(1)))
                .setSubject(USER_ID)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        var cookie = new DefaultCookie(COOKIE_NAME, jwt);
        webClientSession.cookieStore().put(cookie);

        webClientSession.get(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to session-status endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.bodyAsJsonObject().getString("status")).isEqualTo("INVALID_SESSION");
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void returnsAccessTokenGivenSessionCookie(Vertx vertx, VertxTestContext vertxTestContext) {
        var webClient = WebClient.create(vertx);
        var webClientSession = WebClientSession.create(webClient);

        var token = Jwts.builder()
                .setSubject(USER_ID)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        var cookie = new DefaultCookie(COOKIE_NAME, token);
        webClientSession.cookieStore().put(cookie);

        webClientSession.get(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to session-status endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        HttpResponse<Buffer> response = ar.result();
                        assertThat(response.statusCode()).isEqualTo(200);
                        var body = response.bodyAsJsonObject();
                        assertThat(body.getString("status")).isEqualTo("VALID_SESSION");
                        assertThat(body.getString("token")).isNotBlank();
                    });
                    vertxTestContext.completeNow();
                });
    }

    @Test
    public void extendsSessionCookie(Vertx vertx, VertxTestContext vertxTestContext) {
        var webClient = WebClient.create(vertx);
        var webClientSession = WebClientSession.create(webClient);

        var token = Jwts.builder()
                .setSubject(USER_ID)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        var cookie = new DefaultCookie(COOKIE_NAME, token);
        webClientSession.cookieStore().put(cookie);

        webClientSession.get(port, "localhost", PATH)
                .send(ar -> {
                    if (ar.failed()) {
                        var cause = ar.cause();
                        log.error("Request to session-status endpoint failed", cause);
                        vertxTestContext.failNow(cause);
                    }

                    vertxTestContext.verify(() -> {
                        assertThat(ar.succeeded()).isTrue();

                        var newCookie = extractCookie(COOKIE_NAME, ar.result().cookies());
                        var claims = jwt.getJws(newCookie);
                        assertThat(claims).isNotNull();

                        var body = claims.getBody();
                        assertThat(body.getSubject()).isEqualTo(USER_ID);

                        var eightHoursFromNow = Date.from(Instant.now().plus(8, ChronoUnit.HOURS));
                        assertThat(body.getExpiration()).isCloseTo(eightHoursFromNow, FIVE_SECONDS_IN_MILLISECONDS);
                    });
                    vertxTestContext.completeNow();
                });
    }
}
