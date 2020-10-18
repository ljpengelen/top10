package nl.friendlymirror.top10;

import static nl.friendlymirror.top10.session.JwtSessionHandler.AUTHORIZATION_HEADER_NAME;
import static nl.friendlymirror.top10.session.csrf.CsrfTokenHandler.CSRF_TOKEN_HEADER_NAME;

import java.util.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import io.vertx.core.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.account.GoogleAccountVerticle;
import nl.friendlymirror.top10.config.Config;
import nl.friendlymirror.top10.eventbus.MessageCodecs;
import nl.friendlymirror.top10.healthcheck.HealthCheckVerticle;
import nl.friendlymirror.top10.heartbeat.HeartbeatVerticle;
import nl.friendlymirror.top10.jwt.Jwt;
import nl.friendlymirror.top10.migration.MigrationVerticle;
import nl.friendlymirror.top10.quiz.QuizEntityVerticle;
import nl.friendlymirror.top10.quiz.QuizHttpVerticle;
import nl.friendlymirror.top10.session.*;
import nl.friendlymirror.top10.session.csrf.CsrfHeaderChecker;
import nl.friendlymirror.top10.session.csrf.CsrfTokenHandler;

@Log4j2
public class Application {

    private final Config config;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final Vertx vertx;

    public Application(Config config, Vertx vertx) {
        this(config, createGoogleIdTokenVerifier(config), vertx);
    }

    @SneakyThrows
    private static GoogleIdTokenVerifier createGoogleIdTokenVerifier(Config config) {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = new JacksonFactory();
        return new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
                .setAudience(Collections.singletonList(config.getGoogleOauth2ClientId()))
                .build();
    }

    public Application(Config config, GoogleIdTokenVerifier googleIdTokenVerifier, Vertx vertx) {
        this.config = config;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.vertx = vertx;
    }

    public static void main(String[] args) {
        var config = new Config();
        var vertx = Vertx.vertx(config.getVertxOptions());
        var app = new Application(config, vertx);
        app.start().onComplete(ar -> {
            if (ar.succeeded()) {
                log.info("Application started successfully");
            } else {
                log.error("Application failed to start", ar.cause());
            }
        });
    }

    private Future<String> deploy(Verticle verticle, DeploymentOptions deploymentOptions) {
        var promise = Promise.<String> promise();
        vertx.deployVerticle(verticle, deploymentOptions, promise);
        return promise.future();
    }

    private Future<String> deploy(Verticle verticle) {
        return deploy(verticle, new DeploymentOptions());
    }

    private void deployVerticles(Jwt jwt, Promise<Void> result, Router router) {
        log.info("Deploying verticles");

        deploy(new MigrationVerticle(config.getJdbcUrl(), config.getJdbcUsername(), config.getJdbcPassword()), new DeploymentOptions().setWorker(true))
                .onFailure(result::fail)
                .onSuccess(migrationResult ->
                        CompositeFuture.all(List.of(
                                deploy(new HeartbeatVerticle()),
                                deploy(new HealthCheckVerticle(router)),
                                deploy(new GoogleAccountVerticle(config.getJdbcOptions())),
                                deploy(new SessionVerticle(googleIdTokenVerifier, router, config.getJwtSecretKey())),
                                deploy(new SessionStatusVerticle(jwt, router, config.getJwtSecretKey())),
                                deploy(new QuizHttpVerticle(router)),
                                deploy(new QuizEntityVerticle(config.getJdbcOptions())))).onComplete(ar -> {
                            if (ar.succeeded()) {
                                result.complete();
                            } else {
                                result.fail(ar.cause());
                            }
                        }));
    }

    public Future<Void> start() {
        log.info("Starting Top 10");

        var result = Promise.<Void> promise();

        log.info("Registering message codecs");

        MessageCodecs.register(vertx.eventBus());

        log.info("Setting up router");

        var router = Router.router(vertx);

        var corsHandler = CorsHandler.create(config.getCsrfTarget())
                .allowCredentials(true)
                .allowedHeaders(Set.of(AUTHORIZATION_HEADER_NAME, CSRF_TOKEN_HEADER_NAME, "content-type"))
                .exposedHeader(CSRF_TOKEN_HEADER_NAME);
        router.route().handler(corsHandler);

        ErrorHandlers.configure(router);

        router.route("/session/*").handler(new CsrfHeaderChecker(config.getCsrfTarget()));
        var jwt = new Jwt(config.getJwtSecretKey());
        router.route("/session/*").handler(new CsrfTokenHandler(jwt, config.getJwtSecretKey()));
        router.route("/private/*").handler(new JwtSessionHandler(jwt));

        log.info("Setting up HTTP server");

        var server = vertx.createHttpServer();
        server.requestHandler(router);
        var port = config.getHttpPort();
        server.listen(port, ar -> {
            if (ar.succeeded()) {
                log.info("Listening for HTTP requests on port {}", port);
                deployVerticles(jwt, result, router);
            } else {
                log.error("Failed to listen for HTTP requests on port {}", port, ar.cause());
                result.fail(ar.cause());
            }
        });

        return result.future();
    }
}
