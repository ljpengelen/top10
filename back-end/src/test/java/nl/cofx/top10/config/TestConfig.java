package nl.cofx.top10.config;

import org.apache.commons.lang3.*;

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
        return new JsonObject()
                .put("url", jdbcUrl)
                .put("user", jdbcUsername)
                .put("password", jdbcPassword);
    }

    @Override
    protected String fetchMandatoryString(String name) {
        var value = System.getenv(name);

        if (StringUtils.isBlank(value)) {
            return RandomStringUtils.random(10);
        }

        return value;
    }

    @Override
    protected int fetchMandatoryInt(String name) {
        var value = System.getenv(name);

        if (StringUtils.isBlank(value)) {
            return RandomUtils.nextInt();
        }

        return Integer.parseInt(value);
    }
}
