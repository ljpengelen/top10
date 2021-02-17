package nl.cofx.top10;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.cofx.top10.config.TestConfig;

@ExtendWith(VertxExtension.class)
class ApplicationIntegrationTest {

    private final TestConfig config = new TestConfig();

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        var application = new Application(config, vertx);
        application.start().onComplete(vertxTestContext.succeedingThenComplete());
    }

    @Test
    public void starts(Vertx vertx, VertxTestContext vertxTestContext) {
        vertxTestContext.verify(() -> assertThat(vertx.deploymentIDs()).hasSize(10));
        vertxTestContext.completeNow();
    }
}
