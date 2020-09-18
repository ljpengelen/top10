package nl.friendlymirror.top10;

import java.util.Collections;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import lombok.SneakyThrows;
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

    private static final Config CONFIG = new Config();

    private HttpServer server;
    private Vertx vertx;

    public static void main(String[] args) {
        var app = new Application();
        app.start();
    }

    private Future<String> deploy(Verticle verticle, DeploymentOptions deploymentOptions) {
        var promise = Promise.<String> promise();
        vertx.deployVerticle(verticle, deploymentOptions, promise);
        return promise.future();
    }

    private Future<String> deploy(Verticle verticle) {
        return deploy(verticle, new DeploymentOptions());
    }

    @SneakyThrows
    private void deployVerticles(Jwt jwt, Router router) {
        log.info("Deploying verticles");

        deploy(new MigrationVerticle(CONFIG.getJdbcUrl(), CONFIG.getJdbcUsername(), CONFIG.getJdbcPassword()), new DeploymentOptions().setWorker(true));

        deploy(new HeartbeatVerticle());
        deploy(new HealthCheckVerticle(router));
        deploy(new GoogleAccountVerticle(CONFIG.getJdbcOptions()));
        var jsonFactory = new JacksonFactory();
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var verifier = new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
                .setAudience(Collections.singletonList(CONFIG.getGoogleOauth2ClientId()))
                .build();
        deploy(new LogInVerticle(verifier, router, CONFIG.getJwtSecretKey()));
        deploy(new SessionStatusVerticle(jwt, router, CONFIG.getJwtSecretKey()));
        deploy(new EchoVerticle(router));
    }

    public void start() {
        log.info("Starting Top 10");

        vertx = Vertx.vertx(CONFIG.getVertxOptions());

        log.info("Setting up HTTP server");

        var router = Router.router(vertx);
        router.errorHandler(500, routingContext -> log.error("Something went wrong", routingContext.failure()));
        router.route("/session/*").handler(new CsrfHeaderChecker(CONFIG.getCsrfTarget()));
        var jwt = new Jwt(CONFIG.getJwtSecretKey());
        router.route("/session/*").handler(new CsrfTokenHandler(jwt, CONFIG.getJwtSecretKey()));
        router.route("/private/*").handler(new JwtSessionHandler(jwt));

        server = vertx.createHttpServer();
        server.requestHandler(router);
        var port = CONFIG.getHttpPort();
        server.listen(port, ar -> {
            if (ar.succeeded()) {
                log.info("Listening for HTTP requests on port {}", port);
                deployVerticles(jwt, router);
            } else {
                log.error("Failed to listen for HTTP requests on port {}", port, ar.cause());
            }
        });
    }
}
