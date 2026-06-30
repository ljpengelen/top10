package nl.cofx.top10.session;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.cofx.top10.config.Config;

import java.util.Collections;
import java.util.Optional;

@Slf4j
public class GoogleOauth2 {

    private static final HttpTransport HTTP_TRANSPORT = httpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    private final String clientId;
    private final String clientSecret;
    private final String endpoint;
    private final String redirectUri;
    private final String scope;

    public GoogleOauth2(Config config) {
        clientId = config.getGoogleOauth2ClientId();
        clientSecret = config.getGoogleOauth2ClientSecret();
        endpoint = config.getGoogleOauth2Endpoint();
        redirectUri = config.getGoogleOauth2RedirectUrl();
        scope = config.getGoogleOauth2Scope();

        googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(HTTP_TRANSPORT, JSON_FACTORY)
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @SneakyThrows
    private static HttpTransport httpTransport() {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    public Optional<JsonObject> getUser(String code) {
        var optionalGoogleIdToken = getIdToken(code);
        if (optionalGoogleIdToken.isEmpty()) return Optional.empty();

        var googleIdToken = optionalGoogleIdToken.get();
        var payload = googleIdToken.getPayload();
        var jsonObject = new JsonObject()
                .put("name", payload.get("name"))
                .put("emailAddress", payload.getEmail())
                .put("id", payload.getSubject())
                .put("provider", "google");

        return Optional.of(jsonObject);
    }

    private Optional<GoogleIdToken> getIdToken(String code) {
        try {
            var request = new GoogleAuthorizationCodeTokenRequest(HTTP_TRANSPORT, JSON_FACTORY, clientId, clientSecret, code, redirectUri);
            var idTokenString = request.execute().getIdToken();

            return Optional.of(googleIdTokenVerifier.verify(idTokenString));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void redirectToLoginForm(RoutingContext routingContext, String state) {
        routingContext.redirect(endpoint + "?response_type=code&" +
                "scope=" + scope + "&" +
                "redirect_uri=" + redirectUri + "&" +
                "state=" + state + "&" +
                "client_id=" + clientId);
    }
}
