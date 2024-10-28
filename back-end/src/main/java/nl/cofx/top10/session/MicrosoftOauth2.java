package nl.cofx.top10.session;

import com.azure.identity.AuthorizationCodeCredentialBuilder;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import nl.cofx.top10.config.Config;

public class MicrosoftOauth2 {

    private static final String[] SCOPES = {"openid", "offline_access", "User.Read"};

    private final String clientId;
    private final String clientSecret;
    private final String endpoint;
    private final String redirectUrl;
    private final String scope;

    public MicrosoftOauth2(Config config) {
        clientId = config.getMicrosoftOauth2ClientId();
        clientSecret = config.getMicrosoftOauth2ClientSecret();
        endpoint = config.getMicrosoftOauth2Endpoint();
        redirectUrl = config.getMicrosoftOauth2RedirectUrl();
        scope = config.getMicrosoftOauth2Scope();
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
                .redirectUrl(redirectUrl)
                .tenantId("common")
                .build();

        var graphServiceClient = new GraphServiceClient(authProvider, SCOPES);

        return graphServiceClient.me().get();
    }

    public void redirectToLoginForm(RoutingContext routingContext, String state) {
        routingContext.redirect(endpoint + "?response_type=code&" +
                "scope=" + scope + "&" +
                "redirect_uri=" + redirectUrl + "&" +
                "state=" + state + "&" +
                "client_id=" + clientId);
    }
}
