package nl.friendlymirror.top10.heartbeat;

import io.vertx.core.AbstractVerticle;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class HeartbeatVerticle extends AbstractVerticle {

    private static final int INTERVAL_IN_MILLISECONDS = 60 * 1000;

    private long timerId;

    @Override
    public void start() {
        log.info("Starting with {}ms interval", INTERVAL_IN_MILLISECONDS);
        timerId = vertx.setPeriodic(INTERVAL_IN_MILLISECONDS, id -> log.info("❤️"));
    }

    @Override
    public void stop() {
        log.info("Stopping");
        vertx.cancelTimer(timerId);
    }
}
