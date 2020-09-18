package nl.friendlymirror.top10.config;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class TestConfig extends Config {

    private static final String JDBC_POSTGRES_URL = "TEST_JDBC_POSTGRES_URL";
    private static final String JDBC_POSTGRES_USERNAME = "TEST_JDBC_POSTGRES_USERNAME";
    private static final String JDBC_POSTGRES_PASSWORD = "TEST_JDBC_POSTGRES_PASSWORD";

    private final JsonObject jdbcOptions = fetchJdbcOptions();
    private final String jdbcUrl = fetchMandatoryString(JDBC_POSTGRES_URL);
    private final String jdbcUsername = fetchMandatoryString(JDBC_POSTGRES_USERNAME);
    private final String jdbcPassword = fetchOptionalString(JDBC_POSTGRES_PASSWORD);

    @Override
    protected JsonObject fetchJdbcOptions() {
        var jdbcOptions = new JsonObject();
        jdbcOptions.put("url", fetchMandatoryString(JDBC_POSTGRES_URL));
        jdbcOptions.put("user", fetchMandatoryString(JDBC_POSTGRES_USERNAME));
        jdbcOptions.put("password", fetchOptionalString(JDBC_POSTGRES_PASSWORD));
        jdbcOptions.put("ssl", fetchMandatoryString("TEST_JDBC_POSTGRES_USE_SSL"));

        return jdbcOptions;
    }
}
