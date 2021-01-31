package nl.cofx.top10.migration;

import org.flywaydb.core.Flyway;

import io.vertx.core.AbstractVerticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class MigrationVerticle extends AbstractVerticle {

    private final String url;
    private final String username;
    private final String password;

    @Override
    public void start() {
        log.info("Starting");

        var flyway = Flyway.configure().dataSource(url, username, password).load();
        flyway.migrate();
    }
}
