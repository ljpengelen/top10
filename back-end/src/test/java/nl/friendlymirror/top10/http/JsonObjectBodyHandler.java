package nl.friendlymirror.top10.http;

import java.net.http.HttpResponse;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class JsonObjectBodyHandler implements HttpResponse.BodyHandler<JsonObject> {

    @Override
    public HttpResponse.BodySubscriber<JsonObject> apply(HttpResponse.ResponseInfo responseInfo) {
        return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), body -> new JsonObject(Buffer.buffer(body)));
    }
}
