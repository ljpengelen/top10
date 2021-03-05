package nl.cofx.top10.config;

import javax.crypto.SecretKey;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class ProdConfig extends AbstractConfig implements Config {

    private final String csrfTarget = fetchMandatoryString("CSRF_TARGET");
    private final String googleOauth2ClientId = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_ID");
    private final String googleOauth2ClientSecret = fetchMandatoryString("GOOGLE_OAUTH2_CLIENT_SECRET");
    private final String googleOauth2RedirectUri = fetchMandatoryString("GOOGLE_OAUTH2_REDIRECT_URI");
    private final String microsoftOauth2ClientId = fetchMandatoryString("MICROSOFT_OAUTH2_CLIENT_ID");
    private final String microsoftOauth2ClientSecret = fetchMandatoryString("MICROSOFT_OAUTH2_CLIENT_SECRET");
    private final String microsoftOauth2RedirectUri = fetchMandatoryString("MICROSOFT_OAUTH2_REDIRECT_URI");
    private final int httpPort = fetchMandatoryInt("HTTP_PORT");
    private final String jdbcUrl = fetchMandatoryString("JDBC_POSTGRES_URL");
    private final String jdbcUsername = fetchMandatoryString("JDBC_POSTGRES_USERNAME");
    private final String jdbcPassword = fetchOptionalString("JDBC_POSTGRES_PASSWORD");
    private final JsonObject jdbcOptions = fetchJdbcOptions();
    private final SecretKey jwtSecretKey = fetchJwtSecretKey("JWT_ENCODED_SECRET_KEY");

    protected JsonObject fetchJdbcOptions() {
        return new JsonObject()
                .put("url", jdbcUrl)
                .put("user", jdbcUsername)
                .put("password", jdbcPassword);
    }
}
