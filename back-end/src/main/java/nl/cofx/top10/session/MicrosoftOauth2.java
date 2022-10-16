package nl.cofx.top10.session;

import java.util.List;

import com.azure.identity.AuthorizationCodeCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;

import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;
import nl.cofx.top10.InvalidCredentialsException;
import nl.cofx.top10.config.Config;

@Log4j2
public class MicrosoftOauth2 {

    private static final List<String> SCOPES = List.of("openid", "offline_access", "User.Read");

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
            var authProvider = new AuthorizationCodeCredentialBuilder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .authorizationCode(code)
                    .redirectUrl(redirectUri)
                    .tenantId("common")
                    .build();

            var tokenCredentialAuthProvider = new TokenCredentialAuthProvider(SCOPES, authProvider);

            var graphServiceClient = GraphServiceClient.builder()
                    .authenticationProvider(tokenCredentialAuthProvider)
                    .buildClient();

            return graphServiceClient.me().buildRequest().get();
        } catch (ClientException e) {
            log.debug("Unable to get user for authorization code \"{}\"", code, e);
            throw new InvalidCredentialsException(String.format("Invalid authorization code: \"%s\"", code));
        }
    }
}
