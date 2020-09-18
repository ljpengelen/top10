package nl.friendlymirror.top10.session;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class JwtSessionUser implements User {

    @EqualsAndHashCode.Include
    private final JsonObject principal;

    public JwtSessionUser(String userId) {
        principal = new JsonObject().put("userId", userId);
    }

    @Override
    @SuppressWarnings("deprecation")
    public User isAuthorized(String authority, Handler<AsyncResult<Boolean>> resultHandler) {
        throw new IllegalStateException();
    }

    @Override
    @SuppressWarnings("deprecation")
    public User clearCache() {
        throw new IllegalStateException();
    }

    @Override
    public JsonObject principal() {
        return principal;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setAuthProvider(AuthProvider authProvider) {
        throw new IllegalStateException();
    }
}
