package nl.cofx.top10.config;

import javax.crypto.SecretKey;

import org.apache.commons.lang3.RandomStringUtils;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import nl.cofx.top10.RandomPort;

@Getter
public class TestConfig extends AbstractConfig implements Config {

    private final String csrfTarget = fetchMandatoryString("TEST_CSRF_TARGET");
    private final String googleOauth2ClientId = randomString();
    private final String googleOauth2ClientSecret = randomString();
    private final String googleOauth2RedirectUri = "https://www.example.org/oauth2";
    private final int httpPort = RandomPort.get();
    private final String jdbcUrl = fetchMandatoryString("TEST_JDBC_POSTGRES_URL");
    private final String jdbcUsername = fetchMandatoryString("TEST_JDBC_POSTGRES_USERNAME");
    private final String jdbcPassword = fetchOptionalString("TEST_JDBC_POSTGRES_PASSWORD");
    private final JsonObject jdbcOptions = fetchJdbcOptions();
    private final SecretKey jwtSecretKey = fetchJwtSecretKey("TEST_JWT_ENCODED_SECRET_KEY");

    protected JsonObject fetchJdbcOptions() {
        return new JsonObject()
                .put("url", jdbcUrl)
                .put("user", jdbcUsername)
                .put("password", jdbcPassword);
    }

    protected String randomString() {
        return RandomStringUtils.random(10);
    }
}
