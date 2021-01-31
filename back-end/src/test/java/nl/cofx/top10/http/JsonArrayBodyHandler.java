package nl.cofx.top10.http;

import java.net.http.HttpResponse;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;

public class JsonArrayBodyHandler implements HttpResponse.BodyHandler<JsonArray> {

    @Override
    public HttpResponse.BodySubscriber<JsonArray> apply(HttpResponse.ResponseInfo responseInfo) {
        return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), body -> new JsonArray(Buffer.buffer(body)));
    }
}
