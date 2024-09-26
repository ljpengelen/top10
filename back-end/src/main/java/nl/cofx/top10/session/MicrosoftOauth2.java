package nl.cofx.top10.session;

import com.azure.identity.AuthorizationCodeCredentialBuilder;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.vertx.core.json.JsonObject;
import nl.cofx.top10.config.Config;

public class MicrosoftOauth2 {

    private static final String[] SCOPES = {"openid", "offline_access", "User.Read"};

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
                .put("name", user.getDisplayName())
                .put("emailAddress", user.getMail() != null ? user.getMail() : user.getUserPrincipalName())
                .put("id", user.getId())
                .put("provider", "microsoft");
    }

    private User getUserFromGraph(String code) {
        var authProvider = new AuthorizationCodeCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationCode(code)
                .redirectUrl(redirectUri)
                .tenantId("common")
                .build();

        var graphServiceClient = new GraphServiceClient(authProvider, SCOPES);

        return graphServiceClient.me().get();
    }
}
