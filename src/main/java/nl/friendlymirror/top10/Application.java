package nl.friendlymirror.top10;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.healthcheck.HealthCheckVerticle;
import nl.friendlymirror.top10.heartbeat.HeartbeatVerticle;

@Log4j2
public class Application {

    private final Config config = new Config();

    private Vertx vertx;

    public static void main(String[] args) {
        var app = new Application();
        app.start();
    }

    public void start() {
        log.info("Starting Top 10");

        var options = new VertxOptions();
        options.setHAEnabled(true);
        vertx = Vertx.vertx(options);

        log.info("Deploying verticles");

        vertx.deployVerticle(new HeartbeatVerticle());
        vertx.deployVerticle(new HealthCheckVerticle());
    }
}
