package nl.friendlymirror.top10.config;

import javax.crypto.SecretKey;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class Config extends AbstractConfig {

    private static final String JDBC_POSTGRES_URL = "JDBC_POSTGRES_URL";
    private static final String JDBC_POSTGRES_USERNAME = "JDBC_POSTGRES_USERNAME";
    private static final String JDBC_POSTGRES_PASSWORD = "JDBC_POSTGRES_PASSWORD";

    private final SecretKey csrfSecretKey = fetchCsrfSecretKey();
    private final String csrfTarget = fetchMandatoryString("CSRF_TARGET");
    private final String googleOauth2ClientId = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_ID");
    private final String googleOauth2ClientSecret = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_SECRET");
    private final JsonObject jdbcOptions = fetchJdbcOptions();
    private final String jdbcUrl = fetchMandatoryString(JDBC_POSTGRES_URL);
    private final String jdbcUsername = fetchMandatoryString(JDBC_POSTGRES_USERNAME);
    private final String jdbcPassword = fetchOptionalString(JDBC_POSTGRES_PASSWORD);
    private final int httpPort = fetchMandatoryInt("HTTP_PORT");
    private final VertxOptions vertxOptions = fetchVertxOptions();

    private SecretKey fetchCsrfSecretKey() {
        var encodedSecretKey = fetchMandatoryString("CSRF_ENCODED_SECRET_KEY");
        var decodedSecretKey = Decoders.BASE64.decode(encodedSecretKey);

        return Keys.hmacShaKeyFor(decodedSecretKey);
    }

    private JsonObject fetchJdbcOptions() {
        var jdbcOptions = new JsonObject();
        jdbcOptions.put("url", fetchMandatoryString(JDBC_POSTGRES_URL));
        jdbcOptions.put("user", fetchMandatoryString(JDBC_POSTGRES_USERNAME));
        jdbcOptions.put("password", fetchOptionalString(JDBC_POSTGRES_PASSWORD));
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
