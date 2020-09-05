package nl.friendlymirror.top10.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class LogInVerticle extends AbstractVerticle {

    private final Router router;

    private void handle(RoutingContext routingContext) {
        log.info("Root!");
        routingContext.put("path", routingContext.request().path());

        routingContext.next();
    }

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.GET, "/").handler(this::handle);
    }
}
