package nl.friendlymirror.top10;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

public class UserHandler {

    private int accountId;

    public void handle(RoutingContext routingContext) {
        routingContext.setUser(User.create(new JsonObject().put("accountId", accountId)));
        routingContext.next();
    }

    public void logIn(int accountId) {
        this.accountId = accountId;
    }
}
