package nl.friendlymirror.top10;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class EchoVerticle extends AbstractVerticle {

    private final Router router;

    private void handle(RoutingContext routingContext) {
        log.info("Echo");

        routingContext.response()
                .putHeader("content-type", "application/json")
                .end(routingContext.getBodyAsJson().toBuffer());
    }

    @Override
    public void start() {
        log.info("Starting");

        router.route(HttpMethod.POST, "/private/echo").handler(BodyHandler.create());
        router.route(HttpMethod.POST, "/private/echo").handler(this::handle);
    }
}
