package nl.cofx.top10.migration;

import io.vertx.core.AbstractVerticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;

@Slf4j
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
