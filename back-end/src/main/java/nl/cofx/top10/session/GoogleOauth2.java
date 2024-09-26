package nl.cofx.top10.session;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.InvalidCredentialsException;
import nl.cofx.top10.config.Config;

import java.util.Collections;

@Log4j2
public class GoogleOauth2 {

    private static final HttpTransport HTTP_TRANSPORT = httpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOauth2(Config config) {
        clientId = config.getGoogleOauth2ClientId();
        clientSecret = config.getGoogleOauth2ClientSecret();
        redirectUri = config.getGoogleOauth2RedirectUri();
        googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(HTTP_TRANSPORT, JSON_FACTORY)
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @SneakyThrows
    private static HttpTransport httpTransport() {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    public JsonObject getUser(String code) {
        var googleIdToken = getIdToken(code);
        var payload = googleIdToken.getPayload();

        return new JsonObject()
                .put("name", payload.get("name"))
                .put("emailAddress", payload.getEmail())
                .put("id", payload.getSubject())
                .put("provider", "google");
    }

    private GoogleIdToken getIdToken(String code) {
        try {
            var request = new GoogleAuthorizationCodeTokenRequest(HTTP_TRANSPORT, JSON_FACTORY, clientId, clientSecret, code, redirectUri);
            var idTokenString = request.execute().getIdToken();

            return googleIdTokenVerifier.verify(idTokenString);
        } catch (Exception e) {
            log.debug("Unable to get ID token for authorization code \"{}\"", code, e);
            throw new InvalidCredentialsException(String.format("Invalid authorization code: \"%s\"", code));
        }
    }
}
