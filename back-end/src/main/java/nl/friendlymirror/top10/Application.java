package nl.friendlymirror.top10;

import java.util.Collections;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import io.vertx.core.*;
import io.vertx.ext.web.Router;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.account.GoogleAccountVerticle;
import nl.friendlymirror.top10.config.Config;
import nl.friendlymirror.top10.healthcheck.HealthCheckVerticle;
import nl.friendlymirror.top10.heartbeat.HeartbeatVerticle;
import nl.friendlymirror.top10.jwt.Jwt;
import nl.friendlymirror.top10.migration.MigrationVerticle;
import nl.friendlymirror.top10.session.*;
import nl.friendlymirror.top10.session.csrf.CsrfHeaderChecker;
import nl.friendlymirror.top10.session.csrf.CsrfTokenHandler;

@Log4j2
public class Application {

    private final Config config;
    private final Vertx vertx;

    public Application(Config config, Vertx vertx) {
        this.config = config;
        this.vertx = vertx;
    }

    public static void main(String[] args) {
        var config = new Config();
        var vertx = Vertx.vertx(config.getVertxOptions());
        var app = new Application(config, vertx);
        app.start().setHandler(ar -> {
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

    private void deployVerticles(GoogleIdTokenVerifier googleIdTokenVerifier, Jwt jwt, Promise<Void> result, Router router) {
        log.info("Deploying verticles");

        deploy(new MigrationVerticle(config.getJdbcUrl(), config.getJdbcUsername(), config.getJdbcPassword()), new DeploymentOptions().setWorker(true))
                .onFailure(result::fail)
                .onSuccess(migrationResult ->
                        CompositeFuture.all(
                                deploy(new HeartbeatVerticle()),
                                deploy(new HealthCheckVerticle(router)),
                                deploy(new GoogleAccountVerticle(config.getJdbcOptions())),
                                deploy(new LogInVerticle(googleIdTokenVerifier, router, config.getJwtSecretKey())),
                                deploy(new SessionStatusVerticle(jwt, router, config.getJwtSecretKey())),
                                deploy(new EchoVerticle(router))).setHandler(ar -> {
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

        try {
            log.info("Creating Google ID token verifier");

            var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            var jsonFactory = new JacksonFactory();
            var googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
                    .setAudience(Collections.singletonList(config.getGoogleOauth2ClientId()))
                    .build();
            start(googleIdTokenVerifier, result);
        } catch (Exception e) {
            log.error("Unable to create trusted HTTP transport", e);
            result.fail(e);
        }

        return result.future();
    }

    public void start(GoogleIdTokenVerifier googleIdTokenVerifier, Promise<Void> result) {
        log.info("Setting up router");

        var router = Router.router(vertx);
        router.errorHandler(500, routingContext -> log.error("Something went wrong", routingContext.failure()));
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
                deployVerticles(googleIdTokenVerifier, jwt, result, router);
            } else {
                log.error("Failed to listen for HTTP requests on port {}", port, ar.cause());
                result.fail(ar.cause());
            }
        });
    }
}
