package nl.friendlymirror.top10.session.csrf;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class CsrfHeaderChecker implements Handler<RoutingContext> {

    private final String target;
    private final Buffer errorResponse;

    public CsrfHeaderChecker(String target) {
        this.target = target;
        errorResponse = new JsonObject()
                .put("error", String.format("Origin and referer do not match \"%s\"", target))
                .toBuffer();
    }

    public void handle(RoutingContext routingContext) {
        if (hasValidOriginOrReferer(routingContext.request())) {
            routingContext.next();
            return;
        }

        routingContext.response()
                .setStatusCode(400)
                .putHeader("content-type", "application/json")
                .end(errorResponse);
    }

    private boolean hasValidOriginOrReferer(HttpServerRequest request) {
        String origin = request.getHeader("Origin");
        if (StringUtils.isNotBlank(origin)) {
            return matchesTarget(origin);
        }

        String referer = request.getHeader("Referer");
        if (StringUtils.isNotBlank(referer)) {
            return matchesTarget(referer);
        }

        return false;
    }

    private boolean matchesTarget(String headerValue) {
        try {
            URL targetUrl = new URL(target);
            URL headerUrl = new URL(headerValue);

            return headerUrl.getPort() == targetUrl.getPort() &&
                   headerUrl.getHost().equalsIgnoreCase(targetUrl.getHost()) &&
                   headerUrl.getProtocol().equalsIgnoreCase(targetUrl.getProtocol());
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
