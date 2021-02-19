package nl.cofx.top10.config;

import javax.crypto.SecretKey;

import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;

public interface Config {

    String getCsrfTarget();

    String getGoogleOauth2ClientId();

    String getGoogleOauth2ClientSecret();

    String getGoogleOauth2RedirectUri();

    int getHttpPort();

    String getJdbcUrl();

    String getJdbcUsername();

    String getJdbcPassword();

    JsonObject getJdbcOptions();

    SecretKey getJwtSecretKey();

    default VertxOptions getVertxOptions() {
        return new VertxOptions().setHAEnabled(true);
    }
}
