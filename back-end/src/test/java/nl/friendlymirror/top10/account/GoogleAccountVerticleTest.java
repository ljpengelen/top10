package nl.friendlymirror.top10.account;

import static nl.friendlymirror.top10.account.GoogleAccountVerticle.GOOGLE_LOGIN_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.log4j.Log4j2;
import nl.friendlymirror.top10.config.TestConfig;
import nl.friendlymirror.top10.migration.MigrationVerticle;

@ExtendWith(VertxExtension.class)
class GoogleAccountVerticleTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private EventBus eventBus;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.completing());
    }

    @BeforeEach
    public void cleanUp() throws SQLException {
        var connection = DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();
    }

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        eventBus = vertx.eventBus();
        vertx.deployVerticle(new GoogleAccountVerticle(TEST_CONFIG.getJdbcOptions()), vertxTestContext.completing());
    }

    @Test
    public void createsAccount(VertxTestContext vertxTestContext) {
        var googleUserData = new JsonObject()
                .put("id", "abcd")
                .put("name", "John Doe")
                .put("emailAddress", "john.doe@example.com");
        eventBus.request(GOOGLE_LOGIN_ADDRESS, googleUserData, asyncAccountId -> {
            vertxTestContext.verify(() -> {
                assertThat(asyncAccountId.succeeded()).isTrue();
                assertThat(asyncAccountId.result().body()).isNotNull();
            });
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void retrievesExistingAccount(VertxTestContext vertxTestContext) {
        var googleUserData = new JsonObject()
                .put("id", "abcd")
                .put("name", "John Doe")
                .put("emailAddress", "john.doe@example.com");
        eventBus.request(GOOGLE_LOGIN_ADDRESS, googleUserData, asyncNewAccountId -> {
            vertxTestContext.verify(() -> {
                assertThat(asyncNewAccountId.succeeded()).isTrue();
                assertThat(asyncNewAccountId.result().body()).isNotNull();
            });
            eventBus.request(GOOGLE_LOGIN_ADDRESS, googleUserData, asyncExistingAccountId -> {
                vertxTestContext.verify(() -> {
                    assertThat(asyncExistingAccountId.succeeded()).isTrue();
                    assertThat(asyncExistingAccountId.result().body()).isEqualTo(asyncNewAccountId.result().body());
                });
                vertxTestContext.completeNow();
            });
        });
    }
}
