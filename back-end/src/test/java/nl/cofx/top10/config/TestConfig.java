package nl.cofx.top10.config;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import nl.cofx.top10.RandomPort;

@Getter
public class TestConfig extends Config {

    private final String csrfTarget = fetchMandatoryString("TEST_CSRF_TARGET");
    private final int httpPort = RandomPort.get();
    private final String jdbcUrl = fetchMandatoryString("TEST_JDBC_POSTGRES_URL");
    private final String jdbcUsername = fetchMandatoryString("TEST_JDBC_POSTGRES_USERNAME");
    private final String jdbcPassword = fetchOptionalString("TEST_JDBC_POSTGRES_PASSWORD");
    private final JsonObject jdbcOptions = fetchJdbcOptions();

    @Override
    protected JsonObject fetchJdbcOptions() {
        var jdbcOptions = new JsonObject();
        jdbcOptions.put("url", jdbcUrl);
        jdbcOptions.put("user", jdbcUsername);
        jdbcOptions.put("password", jdbcPassword);
        jdbcOptions.put("ssl", fetchMandatoryString("TEST_JDBC_POSTGRES_USE_SSL"));

        return jdbcOptions;
    }
}