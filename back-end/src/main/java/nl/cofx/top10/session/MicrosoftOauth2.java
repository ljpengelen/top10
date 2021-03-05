package nl.cofx.top10.session;

import java.util.List;

import com.microsoft.graph.auth.confidentialClient.AuthorizationCodeProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.User;
import com.microsoft.graph.requests.extensions.GraphServiceClient;

import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.InvalidCredentialsException;
import nl.cofx.top10.config.Config;

@Log4j2
public class MicrosoftOauth2 {

    private static final List<String> SCOPES = List.of("openid", "email", "profile");

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public MicrosoftOauth2(Config config) {
        clientId = config.getMicrosoftOauth2ClientId();
        clientSecret = config.getMicrosoftOauth2ClientSecret();
        redirectUri = config.getMicrosoftOauth2RedirectUri();
    }

    public JsonObject getUser(String code) {
        var user = getUserFromGraph(code);

        return new JsonObject()
                .put("name", user.displayName)
                .put("emailAddress", user.mail != null ? user.mail : user.userPrincipalName)
                .put("id", user.id)
                .put("provider", "microsoft");
    }

    private User getUserFromGraph(String code) {
        try {
            var authProvider = new AuthorizationCodeProvider(clientId, SCOPES, code, redirectUri, clientSecret);
            var graphServiceClient = GraphServiceClient.builder()
                    .authenticationProvider(authProvider)
                    .buildClient();

            return graphServiceClient.me().buildRequest().get();
        } catch (ClientException e) {
            log.debug("Unable to get user for authorization code \"{}\"", code, e);
            throw new InvalidCredentialsException(String.format("Invalid authorization code: \"%s\"", code));
        }
    }
}
