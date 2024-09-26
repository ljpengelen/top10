package nl.cofx.top10.account;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import nl.cofx.top10.config.TestConfig;
import nl.cofx.top10.migration.MigrationVerticle;
import nl.cofx.top10.random.TokenGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.DriverManager;
import java.sql.SQLException;

import static nl.cofx.top10.account.ExternalAccountVerticle.EXTERNAL_LOGIN_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class ExternalAccountVerticleTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String NAME = "John Doe";
    private static final String EMAIL_ADDRESS = "john.doe@example.com";

    private EventBus eventBus;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER);
        vertx.deployVerticle(verticle, deploymentOptions, vertxTestContext.succeedingThenComplete());
    }

    @BeforeEach
    public void cleanUp() throws SQLException {
        var connection = DriverManager.getConnection(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var statement = connection.prepareStatement("TRUNCATE TABLE account CASCADE");
        statement.execute();
        connection.close();
    }

    @BeforeEach
    public void deployVerticle(Vertx vertx, VertxTestContext vertxTestContext) {
        eventBus = vertx.eventBus();
        vertx.deployVerticle(new ExternalAccountVerticle(TEST_CONFIG.getJdbcOptions()), vertxTestContext.succeedingThenComplete());
    }

    @Test
    public void createsAccount(VertxTestContext vertxTestContext) {
        var user = new JsonObject()
                .put("id", TokenGenerator.generateToken())
                .put("name", NAME)
                .put("emailAddress", EMAIL_ADDRESS)
                .put("provider", "google");
        eventBus.request(EXTERNAL_LOGIN_ADDRESS, user, asyncAccount -> {
            vertxTestContext.verify(() -> {
                assertThat(asyncAccount.succeeded()).isTrue();
                var body = asyncAccount.result().body();
                assertThat(body).isNotNull();
                assertThat(body).isInstanceOf(JsonObject.class);
                var jsonObject = (JsonObject) body;
                assertThat(jsonObject.getString("accountId")).isNotNull();
                assertThat(jsonObject.getString("name")).isEqualTo(NAME);
                assertThat(jsonObject.getString("emailAddress")).isEqualTo(EMAIL_ADDRESS);
            });
            vertxTestContext.completeNow();
        });
    }

    @Test
    public void retrievesExistingAccount(VertxTestContext vertxTestContext) {
        var user = new JsonObject()
                .put("id", TokenGenerator.generateToken())
                .put("name", NAME)
                .put("emailAddress", EMAIL_ADDRESS)
                .put("provider", "google");
        eventBus.request(EXTERNAL_LOGIN_ADDRESS, user, asyncNewAccount -> {
            var newAccount = (JsonObject) asyncNewAccount.result().body();
            vertxTestContext.verify(() -> {
                assertThat(asyncNewAccount.succeeded()).isTrue();
                assertThat(newAccount).isNotNull();
                assertThat(newAccount.getString("accountId")).isNotNull();
                assertThat(newAccount.getString("name")).isEqualTo(NAME);
                assertThat(newAccount.getString("emailAddress")).isEqualTo(EMAIL_ADDRESS);
            });
            eventBus.request(EXTERNAL_LOGIN_ADDRESS, user, asyncExistingAccount -> {
                var existingAccount = (JsonObject) asyncExistingAccount.result().body();
                vertxTestContext.verify(() -> {
                    assertThat(asyncExistingAccount.succeeded()).isTrue();
                    assertThat(existingAccount).isNotNull();
                    assertThat(existingAccount.getString("accountId")).isEqualTo(newAccount.getString("accountId"));
                    assertThat(existingAccount.getString("name")).isEqualTo(newAccount.getString("name"));
                    assertThat(existingAccount.getString("emailAddress")).isEqualTo(newAccount.getString("emailAddress"));
                });
                vertxTestContext.completeNow();
            });
        });
    }
}
