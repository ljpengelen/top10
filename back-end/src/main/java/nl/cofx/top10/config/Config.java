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
        return new JsonObject()
                .put("url", jdbcUrl)
                .put("user", jdbcUsername)
                .put("password", jdbcPassword);
    }

    private VertxOptions fetchVertxOptions() {
        return new VertxOptions().setHAEnabled(true);
    }
}
