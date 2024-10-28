package nl.cofx.top10.config;

import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;

import javax.crypto.SecretKey;

public interface Config {

    String getCsrfTarget();

    String getGoogleOauth2ClientId();

    String getGoogleOauth2ClientSecret();

    String getGoogleOauth2Endpoint();

    String getGoogleOauth2RedirectUrl();

    String getGoogleOauth2Scope();

    String getHomeUrl();

    String getMicrosoftOauth2ClientId();

    String getMicrosoftOauth2ClientSecret();

    String getMicrosoftOauth2Endpoint();

    String getMicrosoftOauth2RedirectUrl();

    String getMicrosoftOauth2Scope();

    int getHttpPort();

    String getJdbcUrl();

    String getJdbcUsername();

    String getJdbcPassword();

    JsonObject getJdbcOptions();

    SecretKey getJwtSecretKey();

    default VertxOptions getVertxOptions() {
        return new VertxOptions().setHAEnabled(true);
    }

    boolean useSecureCookies();
}
