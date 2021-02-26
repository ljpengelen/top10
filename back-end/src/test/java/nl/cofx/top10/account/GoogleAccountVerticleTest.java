package nl.cofx.top10.account;

import static nl.cofx.top10.account.GoogleAccountVerticle.GOOGLE_LOGIN_ADDRESS;
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
import nl.cofx.top10.migration.MigrationVerticle;
import nl.cofx.top10.config.TestConfig;

@ExtendWith(VertxExtension.class)
class GoogleAccountVerticleTest {

    private static final TestConfig TEST_CONFIG = new TestConfig();

    private static final String NAME = "John Doe";
    private static final String EMAIL_ADDRESS = "john.doe@example.com";

    private EventBus eventBus;

    @BeforeAll
    public static void migrate(Vertx vertx, VertxTestContext vertxTestContext) {
        var verticle = new MigrationVerticle(TEST_CONFIG.getJdbcUrl(), TEST_CONFIG.getJdbcUsername(), TEST_CONFIG.getJdbcPassword());
        var deploymentOptions = new DeploymentOptions().setWorker(true);
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
        vertx.deployVerticle(new GoogleAccountVerticle(TEST_CONFIG.getJdbcOptions()), vertxTestContext.succeedingThenComplete());
    }

    @Test
    public void createsAccount(VertxTestContext vertxTestContext) {
        var googleUserData = new JsonObject()
                .put("id", "037088d6-1bdd-4532-8127-c25359c9e423")
                .put("name", NAME)
                .put("emailAddress", EMAIL_ADDRESS);
        eventBus.request(GOOGLE_LOGIN_ADDRESS, googleUserData, asyncAccount -> {
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
        var googleUserData = new JsonObject()
                .put("id", "abcd")
                .put("name", NAME)
                .put("emailAddress", EMAIL_ADDRESS);
        eventBus.request(GOOGLE_LOGIN_ADDRESS, googleUserData, asyncNewAccount -> {
            var newAccount = asyncNewAccount.result().body();
            vertxTestContext.verify(() -> {
                assertThat(asyncNewAccount.succeeded()).isTrue();
                assertThat(newAccount).isNotNull();
                assertThat(newAccount).isInstanceOf(JsonObject.class);
                var jsonObject = (JsonObject) newAccount;
                assertThat(jsonObject.getString("accountId")).isNotNull();
                assertThat(jsonObject.getString("name")).isEqualTo(NAME);
                assertThat(jsonObject.getString("emailAddress")).isEqualTo(EMAIL_ADDRESS);
            });
            eventBus.request(GOOGLE_LOGIN_ADDRESS, googleUserData, asyncExistingAccount -> {
                vertxTestContext.verify(() -> {
                    assertThat(asyncExistingAccount.succeeded()).isTrue();
                    assertThat(asyncExistingAccount.result().body()).isEqualTo(newAccount);
                });
                vertxTestContext.completeNow();
            });
        });
    }
}
