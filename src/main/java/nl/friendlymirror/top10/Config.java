package nl.friendlymirror.top10;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public class Config {

    private final JsonObject jdbcOptions;

    public Config() {
        jdbcOptions = fetchJdbcOptions();
    }

    private String fetchOptionalString(String name) {
        return System.getenv(name);
    }

    private String fetchMandatoryString(String name) {
        var value = System.getenv(name);

        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException(String.format("Tried to load environment variable \"%s\", which is not set.", name));
        }

        return value;
    }

    private JsonObject fetchJdbcOptions() {
        var jdbcOptions = new JsonObject();

        jdbcOptions.put("url", fetchMandatoryString("JDBC_POSTGRES_URL"));
        jdbcOptions.put("user", fetchMandatoryString("JDBC_POSTGRES_USERNAME"));
        jdbcOptions.put("password", fetchOptionalString("JDBC_POSTGRES_PASSWORD"));
        jdbcOptions.put("ssl", fetchMandatoryString("JDBC_POSTGRES_USE_SSL"));

        return jdbcOptions;
    }
}
