package nl.friendlymirror.top10.http;

import java.net.http.HttpRequest;

import io.vertx.core.json.JsonObject;

public class BodyPublisher {

    public static HttpRequest.BodyPublisher ofJsonObject(JsonObject body) {
        return HttpRequest.BodyPublishers.ofString(body.toString());
    }
}
