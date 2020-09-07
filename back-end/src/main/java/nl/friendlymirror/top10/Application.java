package nl.friendlymirror.top10;

import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.csrf.CsrfHeaderChecker;
import nl.friendlymirror.top10.csrf.CsrfTokenHandler;
import nl.friendlymirror.top10.healthcheck.HealthCheckVerticle;
import nl.friendlymirror.top10.heartbeat.HeartbeatVerticle;
import nl.friendlymirror.top10.jwt.Jwt;
import nl.friendlymirror.top10.session.JwtSessionHandler;
import nl.friendlymirror.top10.web.LogInVerticle;

@Log4j2
public class Application {

    private static final Config config = new Config();

    private HttpServer server;
    private Vertx vertx;

    public static void main(String[] args) {
        var app = new Application();
        app.start();
    }

    private Future<String> deploy(Verticle verticle) {
        var promise = Promise.<String> promise();
        vertx.deployVerticle(verticle, promise);
        return promise.future();
    }

    private void deployVerticles(Router router) {
        log.info("Deploying verticles");

        deploy(new HeartbeatVerticle());
        deploy(new HealthCheckVerticle(router));
        deploy(new LogInVerticle(router, config.getCsrfSecretKey()));
    }

    public void start() {
        log.info("Starting Top 10");

        vertx = Vertx.vertx(config.getVertxOptions());

        log.info("Setting up HTTP server");

        var router = Router.router(vertx);
        router.errorHandler(500, routingContext -> log.error("Something went wrong", routingContext.failure()));
        router.route().handler(new CsrfHeaderChecker(config.getCsrfTarget()));
        var jwt = new Jwt(config.getCsrfSecretKey());
        router.route().handler(new CsrfTokenHandler(jwt, config.getCsrfSecretKey()));
        router.route("/private/*").handler(new JwtSessionHandler(jwt));

        server = vertx.createHttpServer();
        server.requestHandler(router);
        var port = config.getHttpPort();
        server.listen(port, ar -> {
            if (ar.succeeded()) {
                log.info("Listening for HTTP requests on port {}", port);
                deployVerticles(router);
            } else {
                log.error("Failed to listen for HTTP requests on port {}", port, ar.cause());
            }
        });
    }
}
