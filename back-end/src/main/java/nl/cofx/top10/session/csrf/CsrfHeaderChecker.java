package nl.cofx.top10.session.csrf;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class CsrfHeaderChecker implements Handler<RoutingContext> {

    private final List<URL> targetUrls;
    private final Buffer errorResponse;

    public CsrfHeaderChecker(List<String> targets) {
        this.targetUrls = targets.stream()
                .map(CsrfHeaderChecker::toUrl)
                .toList();
        errorResponse = new JsonObject()
                .put("error", String.format("Origin and referer do not match one of %s", targets))
                .toBuffer();
    }

    private static URL toUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void handle(RoutingContext routingContext) {
        var request = routingContext.request();
        if (request.cookies().isEmpty()) {
            routingContext.next();
            return;
        }

        if (hasValidOriginOrReferer(request)) {
            routingContext.next();
            return;
        }

        routingContext.response()
                .setStatusCode(400)
                .putHeader("content-type", "application/json")
                .end(errorResponse);
    }

    private boolean hasValidOriginOrReferer(HttpServerRequest request) {
        var origin = request.getHeader("Origin");
        if (StringUtils.isNotBlank(origin)) {
            return matchesTarget(origin);
        }

        var referer = request.getHeader("Referer");
        if (StringUtils.isNotBlank(referer)) {
            return matchesTarget(referer);
        }

        return false;
    }

    private boolean matchesTarget(String headerValue) {
        try {
            var headerUrl = new URL(headerValue);

            return targetUrls.stream().anyMatch(targetUrl -> match(headerUrl, targetUrl));
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static boolean match(URL first, URL second) {
        return first.getPort() == second.getPort() &&
                first.getHost().equalsIgnoreCase(second.getHost()) &&
                first.getProtocol().equalsIgnoreCase(second.getProtocol());
    }
}
