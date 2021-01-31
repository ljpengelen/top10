package nl.cofx.top10.config;

import javax.crypto.SecretKey;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class Config extends AbstractConfig {

    private final String csrfTarget = fetchMandatoryString("CSRF_TARGET");
    private final String googleOauth2ClientId = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_ID");
    private final String googleOauth2ClientSecret = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_SECRET");
    private final int httpPort = fetchMandatoryInt("HTTP_PORT");
    private final String jdbcUrl = fetchMandatoryString("JDBC_POSTGRES_URL");
    private final String jdbcUsername = fetchMandatoryString("JDBC_POSTGRES_USERNAME");
    private final String jdbcPassword = fetchOptionalString("JDBC_POSTGRES_PASSWORD");
    private final JsonObject jdbcOptions = fetchJdbcOptions();
    private final SecretKey jwtSecretKey = fetchJwtSecretKey();
    private final VertxOptions vertxOptions = fetchVertxOptions();

    private SecretKey fetchJwtSecretKey() {
        var encodedSecretKey = fetchMandatoryString("JWT_ENCODED_SECRET_KEY");
        var decodedSecretKey = Decoders.BASE64.decode(encodedSecretKey);

        return Keys.hmacShaKeyFor(decodedSecretKey);
    }

    protected JsonObject fetchJdbcOptions() {
        var jdbcOptions = new JsonObject();
        jdbcOptions.put("url", jdbcUrl);
        jdbcOptions.put("user", jdbcUsername);
        jdbcOptions.put("password", jdbcPassword);
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
