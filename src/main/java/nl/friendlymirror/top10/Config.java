package nl.friendlymirror.top10;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public class Config {

    private final String googleOauth2ClientId = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_ID");
    private final String googleOauth2ClientSecret = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_SECRET");
    private final JsonObject jdbcOptions = fetchJdbcOptions();
    private final int httpPort = fetchMandatoryInt("HTTP_PORT");
    private final VertxOptions vertxOptions = fetchVertxOptions();

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

    private int fetchMandatoryInt(String name) {
        return Integer.parseInt(fetchMandatoryString(name));
    }

    private JsonObject fetchJdbcOptions() {
        var jdbcOptions = new JsonObject();
        jdbcOptions.put("url", fetchMandatoryString("JDBC_POSTGRES_URL"));
        jdbcOptions.put("user", fetchMandatoryString("JDBC_POSTGRES_USERNAME"));
        jdbcOptions.put("password", fetchOptionalString("JDBC_POSTGRES_PASSWORD"));
        jdbcOptions.put("ssl", fetchMandatoryString("JDBC_POSTGRES_USE_SSL"));

        return jdbcOptions;
    }

    private VertxOptions fetchVertxOptions() {
        var inDevelopmentMode = "dev".equals(fetchMandatoryString("VERTXWEB_ENVIRONMENT"));

        var vertxOptions = new VertxOptions();
        vertxOptions.setHAEnabled(true);
        vertxOptions.getFileSystemOptions().setFileCachingEnabled(!inDevelopmentMode);

        return vertxOptions;
    }
}
