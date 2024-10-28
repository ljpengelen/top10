package nl.cofx.top10.config;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import nl.cofx.top10.PostgresExtension;
import nl.cofx.top10.RandomPort;
import org.apache.commons.lang3.RandomStringUtils;

import javax.crypto.SecretKey;

@Getter
public class TestConfig extends AbstractConfig implements Config {

    private final String csrfTarget = fetchMandatoryString("TEST_CSRF_TARGET");
    private final String googleOauth2ClientId = randomString();
    private final String googleOauth2ClientSecret = randomString();
    private final String googleOauth2Endpoint = "https://accounts.google.com/o/oauth2/v2/auth";
    private final String googleOauth2RedirectUrl = "https://www.example.org/google/oauth2";
    private final String googleOauth2Scope = "openid email profile";
    private final String homeUrl = "http://localhost:9050";
    private final String microsoftOauth2ClientId = randomString();
    private final String microsoftOauth2ClientSecret = randomString();
    private final String microsoftOauth2Endpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private final String microsoftOauth2RedirectUrl = "https://www.example.org/microsoft/oauth2";
    private final String microsoftOauth2Scope = "openid offline_access User.Read";
    private final int httpPort = RandomPort.get();
    private final JsonObject jdbcOptions = fetchJdbcOptions();
    private final SecretKey jwtSecretKey = fetchJwtSecretKey("TEST_JWT_ENCODED_SECRET_KEY");

    @Override
    public String getJdbcUrl() {
        return PostgresExtension.JDBC_URL;
    }

    @Override
    public String getJdbcUsername() {
        return PostgresExtension.USERNAME;
    }

    @Override
    public String getJdbcPassword() {
        return PostgresExtension.PASSWORD;
    }

    @Override
    public boolean useSecureCookies() {
        return false;
    }

    protected JsonObject fetchJdbcOptions() {
        return new JsonObject()
                .put("url", getJdbcUrl())
                .put("user", getJdbcUsername())
                .put("password", getJdbcPassword());
    }

    protected String randomString() {
        return RandomStringUtils.insecure().next(10);
    }
}
