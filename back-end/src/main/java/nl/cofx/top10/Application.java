package nl.cofx.top10;

import static nl.cofx.top10.session.JwtSessionHandler.AUTHORIZATION_HEADER_NAME;
import static nl.cofx.top10.session.csrf.CsrfTokenHandler.CSRF_TOKEN_HEADER_NAME;

import java.util.List;
import java.util.Set;

import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.account.ExternalAccountVerticle;
import nl.cofx.top10.config.Config;
import nl.cofx.top10.config.ProdConfig;
import nl.cofx.top10.eventbus.MessageCodecs;
import nl.cofx.top10.healthcheck.HealthCheckVerticle;
import nl.cofx.top10.heartbeat.HeartbeatVerticle;
import nl.cofx.top10.jwt.Jwt;
import nl.cofx.top10.migration.MigrationVerticle;
import nl.cofx.top10.quiz.*;
import nl.cofx.top10.session.*;
import nl.cofx.top10.session.csrf.CsrfHeaderChecker;
import nl.cofx.top10.session.csrf.CsrfTokenHandler;

@Log4j2
public class Application {

    private final Config config;
    private final GoogleOauth2 googleOauth2;
    private final MicrosoftOauth2 microsoftOauth2;
    private final Vertx vertx;

    public Application(Config config, Vertx vertx) {
        this(config, new GoogleOauth2(config), new MicrosoftOauth2(config), vertx);
    }

    public Application(Config config, GoogleOauth2 googleOauth2, MicrosoftOauth2 microsoftOauth2, Vertx vertx) {
        this.config = config;
        this.googleOauth2 = googleOauth2;
        this.microsoftOauth2 = microsoftOauth2;
        this.vertx = vertx;
    }

    public static void main(String[] args) {
        var config = new ProdConfig();
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

        var jdbcOptions = config.getJdbcOptions();
        var jwtSecretKey = config.getJwtSecretKey();

        deploy(new MigrationVerticle(config.getJdbcUrl(), config.getJdbcUsername(), config.getJdbcPassword()), new DeploymentOptions().setWorker(true))
                .compose(migrationResult ->
                        CompositeFuture.all(List.of(
                                deploy(new HeartbeatVerticle()),
                                deploy(new ExternalAccountVerticle(jdbcOptions)),
                                deploy(new SessionVerticle(googleOauth2, microsoftOauth2, router, jwtSecretKey)),
                                deploy(new SessionStatusVerticle(jwt, router, jwtSecretKey)),
                                deploy(new QuizHttpVerticle(router)),
                                deploy(new QuizEntityVerticle(jdbcOptions)),
                                deploy(new ListHttpVerticle(router)),
                                deploy(new ListEntityVerticle(jdbcOptions)))))
                .compose(deploymentResult -> deploy(new HealthCheckVerticle(jdbcOptions, router)))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        result.complete();
                    } else {
                        result.fail(ar.cause());
                    }
                });
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
                .allowedMethods(Set.of(HttpMethod.DELETE, HttpMethod.PUT))
                .exposedHeader(CSRF_TOKEN_HEADER_NAME);
        router.route().handler(corsHandler);

        ErrorHandlers.configure(router);

        router.route("/session/*").handler(new CsrfHeaderChecker(config.getCsrfTarget()));
        var jwt = new Jwt(config.getJwtSecretKey());
        router.route("/session/*").handler(new CsrfTokenHandler(jwt, config.getJwtSecretKey()));
        router.route("/private/*")
                .handler(new JwtSessionHandler(jwt))
                .handler(new PrivateRouteHandler());
        router.route("/public/*").handler(new JwtSessionHandler(jwt));

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
