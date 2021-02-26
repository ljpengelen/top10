package nl.cofx.top10;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

public class UserHandler {

    private String accountId;

    public void handle(RoutingContext routingContext) {
        if (accountId != null) {
            routingContext.setUser(User.create(new JsonObject().put("accountId", accountId)));
        }
        routingContext.next();
    }

    public void logIn(String accountId) {
        this.accountId = accountId;
    }

    public void logOut() {
        this.accountId = null;
    }
}
