package dev.veryue.vuequests.editor.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.veryue.vuequests.editor.api.models.responses.ApiError;
import dev.veryue.vuequests.editor.QuestEditorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static dev.veryue.vuequests.editor.QuestEditorConstants.API;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CREATE;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CREATE_DEPENDENCY;
import static dev.veryue.vuequests.editor.QuestEditorConstants.OPENAPI;
import static dev.veryue.vuequests.editor.QuestEditorConstants.OPENAPI_RESOURCE;
import static dev.veryue.vuequests.editor.QuestEditorConstants.READ_ROUTES;
import static dev.veryue.vuequests.editor.QuestEditorConstants.WRITE_ROUTES;

public class QuestApiHttpHandler implements HttpHandler {
    public static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final Gson JSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger("vuequests");
    private final QuestApiService service;
    private final String token;

    public QuestApiHttpHandler(QuestApiService service) {
        this.service = service;
        this.token = System.getProperty("vuequests.api.token", "");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            boolean head = exchange.getRequestMethod().equals("HEAD");
            try {
                String path = exchange.getRequestURI().getPath().substring(API.length());
                boolean write = WRITE_ROUTES.contains(path);
                if (!write && !READ_ROUTES.contains(path)) throw new QuestApiException(404, "Endpoint not found.");
                if (!token.isEmpty() && !authorized(exchange)) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                    throw new QuestApiException(401, "Supply the configured API bearer token.");
                }
                String method = exchange.getRequestMethod();
                if (write ? !method.equals("POST") : !method.equals("GET") && !head) {
                    exchange.getResponseHeaders().set("Allow", write ? "POST" : "GET, HEAD");
                    throw new QuestApiException(405, "Method not allowed.");
                }
                String response;
                if (write) {
                    checkOrigin(exchange);
                    response = service.write(path, readBody(exchange)).get(60, TimeUnit.SECONDS);
                } else if (path.equals(OPENAPI)) {
                    try (InputStream input = QuestApiHttpHandler.class.getResourceAsStream(OPENAPI_RESOURCE)) {
                        if (input == null) throw new IllegalStateException("The bundled OpenAPI contract is missing.");
                        response = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } else {
                    response = service.read(path, parameters(exchange)).get(30, TimeUnit.SECONDS);
                }
                send(exchange, write && path.startsWith(CREATE) && !path.equals(CREATE_DEPENDENCY) ? 201 : 200, response, head);
            } catch (Exception exception) {
                Throwable cause = exception;
                while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null)
                    cause = cause.getCause();
                int status = cause instanceof QuestApiException api ? api.status()
                        : cause instanceof QuestEditorService.ConflictException ? 409
                        : cause instanceof TimeoutException ? 503
                        : cause instanceof IllegalArgumentException || cause instanceof JsonParseException ? 400 : 500;
                if (status == 500) LOGGER.error("Quest API request failed", cause);
                if (cause instanceof InterruptedException) Thread.currentThread().interrupt();
                String message = status == 500 ? "Quest API request failed; check the server log." : cause.getMessage();
                if (message == null || message.isBlank()) message = status == 503
                        ? "Timed out waiting for the game server. Inspect the document before retrying a write." : "Invalid API request.";
                send(exchange, status, JSON.toJson(new ApiError(message)), head);
            }
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        return authorization != null && MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8));
    }

    private static void checkOrigin(HttpExchange exchange) {
        String site = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
        if (site != null && !site.equals("same-origin") && !site.equals("none"))
            throw new QuestApiException(403, "Cross-origin writes are forbidden.");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return;
        URI source = URI.create(origin);
        URI target = URI.create("http://" + exchange.getRequestHeaders().getFirst("Host"));
        int sourcePort = source.getPort() < 0 ? 80 : source.getPort();
        int targetPort = target.getPort() < 0 ? 80 : target.getPort();
        if (!"http".equals(source.getScheme()) || source.getHost() == null || !source.getHost().equalsIgnoreCase(target.getHost()) || sourcePort != targetPort) {
            throw new QuestApiException(403, "Cross-origin writes are forbidden.");
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT).equals("application/json")) {
            throw new QuestApiException(415, "Use Content-Type: application/json.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) throw new QuestApiException(413, "API requests are limited to 1 MiB.");
        String text = new String(body, StandardCharsets.UTF_8);
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            parsed = JSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new JsonParseException("Unexpected data after the request object.");
        } catch (IOException exception) {
            throw new JsonParseException("Supply valid JSON.", exception);
        }
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Supply a JSON request object.");
        checkDepth(parsed, 0);
        return text;
    }

    private static void checkDepth(JsonElement element, int depth) {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting exceeds 64 levels.");
        if (element.isJsonObject())
            element.getAsJsonObject().entrySet().forEach(entry -> checkDepth(entry.getValue(), depth + 1));
        else if (element.isJsonArray()) element.getAsJsonArray().forEach(child -> checkDepth(child, depth + 1));
    }

    private static Map<String, String> parameters(HttpExchange exchange) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) for (String entry : query.split("&")) {
            String[] pair = entry.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            if (parameters.putIfAbsent(name, value) != null)
                throw new IllegalArgumentException("Duplicate query parameter " + name);
        }
        return parameters;
    }

    private static void send(HttpExchange exchange, int status, String json, boolean head) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (head) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
