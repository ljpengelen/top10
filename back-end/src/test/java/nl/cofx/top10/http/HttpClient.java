package nl.cofx.top10.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HttpClient {

    private static final java.net.http.HttpClient HTTP_CLIENT = java.net.http.HttpClient.newHttpClient();

    private final int port;

    public HttpResponse<JsonArray> getQuizzes() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();
        return HTTP_CLIENT.send(request, new JsonArrayBodyHandler());
    }

    public HttpResponse<JsonObject> createQuiz(JsonObject quiz) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(quiz))
                .uri(URI.create("http://localhost:" + port + "/private/quiz"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonObject> participateInQuiz(String quizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + quizId + "/participate"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonObject> getQuizResults(String quizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + quizId + "/result"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<String> getParticipants(String quizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + quizId + "/participants"))
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<JsonObject> completeQuiz(String quizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + quizId + "/complete"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonObject> getQuiz(String quizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/public/quiz/" + quizId))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonObject> finalizeList(String listId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId + "/finalize"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonObject> addVideo(String listId, String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .POST(BodyPublisher.ofJsonObject(new JsonObject().put("url", url)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId + "/video"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonObject> deleteVideo(String videoId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create("http://localhost:" + port + "/private/video/" + videoId))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonObject> assignList(String listId, String accountId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .PUT(BodyPublisher.ofJsonObject(new JsonObject().put("assigneeId", accountId)))
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId + "/assign"))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }

    public HttpResponse<JsonArray> getLists() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list"))
                .build();

        return HTTP_CLIENT.send(request, new JsonArrayBodyHandler());
    }

    public HttpResponse<JsonArray> getLists(String quizId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/quiz/" + quizId + "/list"))
                .build();

        return HTTP_CLIENT.send(request, new JsonArrayBodyHandler());
    }

    public HttpResponse<JsonObject> getList(String listId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + port + "/private/list/" + listId))
                .build();

        return HTTP_CLIENT.send(request, new JsonObjectBodyHandler());
    }
}
