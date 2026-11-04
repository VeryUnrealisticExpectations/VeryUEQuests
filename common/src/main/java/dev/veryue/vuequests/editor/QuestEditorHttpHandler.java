package dev.veryue.vuequests.editor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.veryue.vuequests.exceptions.EditorResourcesUnavailableException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static dev.veryue.vuequests.editor.QuestEditorConstants.API;
import static dev.veryue.vuequests.editor.QuestEditorConstants.ASSETS_FILE;
import static dev.veryue.vuequests.editor.QuestEditorConstants.ASSETS_LIST;
import static dev.veryue.vuequests.editor.QuestEditorConstants.EDITOR;
import static dev.veryue.vuequests.editor.QuestEditorConstants.EDITOR_COMPONENTS;
import static dev.veryue.vuequests.editor.QuestEditorConstants.EDITOR_COMPONENT_PREVIEW;
import static dev.veryue.vuequests.editor.QuestEditorConstants.EDITOR_COMPONENT_VALIDATE;
import static dev.veryue.vuequests.editor.QuestEditorConstants.EDITOR_RESOURCES;

public class QuestEditorHttpHandler implements HttpHandler {

    private static final int STATUS_OK = 200;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final int STATUS_CONFLICT = 409;
    private static final int STATUS_PAYLOAD_TOO_LARGE = 413;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final int STATUS_SERVICE_UNAVAILABLE = 503;

    public static final String RESOURCE_ROOT = "/web/vuequests/";
    private static final Gson JSON = new Gson();
    private static final int MAX_REQUEST_BYTES = 8 * 1024 * 1024;
    private final QuestEditorService service;
    private record ErrorResponse(String error) { }

    public QuestEditorHttpHandler(QuestEditorService service) { this.service = service; }

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("mcmeta", "application/json; charset=utf-8"),
            Map.entry("map", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("wasm", "application/wasm"),
            Map.entry("zip", "application/zip")
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            boolean head = exchange.getRequestMethod().equals("HEAD");
            try {
                if (exchange.getRequestURI().getPath().startsWith(API + "/")) handleApi(exchange, head);
                else serveFrontend(exchange, head);
            } catch (Exception exception) {
                Throwable cause = exception;
                while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) cause = cause.getCause();
                int status = cause instanceof QuestEditorService.ConflictException ? STATUS_CONFLICT
                        : cause instanceof EditorResourcesUnavailableException || cause instanceof TimeoutException ? STATUS_SERVICE_UNAVAILABLE
                        : cause instanceof IllegalArgumentException || cause instanceof JsonParseException ? STATUS_BAD_REQUEST : STATUS_INTERNAL_SERVER_ERROR;
                error(exchange, status, cause.getMessage() == null ? "The editor request failed." : cause.getMessage(), head);
            }
        }

    }

    private void handleApi(HttpExchange exchange, boolean head) throws Exception {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ((path.equals(API + EDITOR_COMPONENT_PREVIEW) || path.equals(API + EDITOR_COMPONENT_VALIDATE)) && method.equals("POST")) {
            JsonObject request = readComponentRequest(exchange, path.equals(API + EDITOR_COMPONENT_PREVIEW) ? 128 * 1024 : MAX_REQUEST_BYTES);
            Object response = path.equals(API + EDITOR_COMPONENT_PREVIEW) ? QuestEditorResources.preview(request).get(3, TimeUnit.SECONDS)
                    : service.validateComponent(request).get(30, TimeUnit.SECONDS);
            json(exchange, STATUS_OK, response, head);
            return;
        }
        if (path.equals(API + EDITOR) && method.equals("PUT")) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (!validOrigin(exchange) || contentType == null || !contentType.startsWith("application/json")) {
                error(exchange, STATUS_FORBIDDEN, "Save requests must originate from the editor.", head);
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
            if (body.length > MAX_REQUEST_BYTES) {
                error(exchange, STATUS_PAYLOAD_TOO_LARGE, "The quest document is too large.", head);
                return;
            }
            QuestEditorService.SaveRequest request = JSON.fromJson(new String(body, StandardCharsets.UTF_8), QuestEditorService.SaveRequest.class);
            if (request == null) throw new IllegalArgumentException("Supply a JSON quest document.");
            json(exchange, STATUS_OK, service.save(request).get(60, TimeUnit.SECONDS), head);
            return;
        }
        if (!method.equals("GET") && !head) {
            exchange.getResponseHeaders().set("Allow", path.equals(API + EDITOR) ? "GET, HEAD, PUT" : path.startsWith(API + EDITOR_COMPONENTS) ? "POST" : "GET, HEAD");
            error(exchange, STATUS_METHOD_NOT_ALLOWED, "Method not allowed.", head);
            return;
        }
        switch (path) {
            case API + EDITOR -> json(exchange, STATUS_OK, service.load().get(30, TimeUnit.SECONDS), head);
            case API + EDITOR_RESOURCES -> {
                String currentEtag = "\"" + QuestEditorAssets.revision() + "\"";
                if (currentEtag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                    exchange.getResponseHeaders().set("ETag", currentEtag);
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                    exchange.sendResponseHeaders(304, -1);
                    return;
                }
                JsonObject resources = QuestEditorResources.describe().get(3, TimeUnit.SECONDS);
                String etag = "\"" + resources.get("assetRevision").getAsString() + "\"";
                exchange.getResponseHeaders().set("ETag", etag);
                if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) exchange.sendResponseHeaders(304, -1);
                else json(exchange, STATUS_OK, resources, head);
            }
            case API + ASSETS_FILE -> {
                String resourcePath = parameter(exchange, "path");
                byte[] resource = QuestEditorResources.read(resourcePath).get(3, TimeUnit.SECONDS);
                assetCacheHeaders(exchange);
                if (resource == null) error(exchange, STATUS_NOT_FOUND, "Resource not found.", head);
                else {
                    send(exchange, STATUS_OK, resource, contentType(resourcePath), head);
                }
            }
            case API + ASSETS_LIST -> {
                var files = QuestEditorResources.list(parameter(exchange, "path")).get(3, TimeUnit.SECONDS);
                assetCacheHeaders(exchange);
                json(exchange, STATUS_OK, files, head);
            }
            default -> error(exchange, STATUS_NOT_FOUND, "Endpoint not found.", head);
        }
    }

    private JsonObject readComponentRequest(HttpExchange exchange, int maximumBytes) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!validOrigin(exchange) || contentType == null || !contentType.startsWith("application/json")) {
            throw new IllegalArgumentException("Component requests must originate from the editor and contain JSON.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(maximumBytes + 1);
        if (body.length > maximumBytes) throw new IllegalArgumentException("The component request is too large.");
        JsonObject request = JSON.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
        if (request == null) throw new IllegalArgumentException("Supply a component request.");
        checkJsonDepth(request, 0);
        return request;
    }

    private static void checkJsonDepth(JsonElement element, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Component nesting exceeds 64 levels.");
        if (element.isJsonObject()) element.getAsJsonObject().entrySet().forEach(entry -> checkJsonDepth(entry.getValue(), depth + 1));
        else if (element.isJsonArray()) element.getAsJsonArray().forEach(child -> checkJsonDepth(child, depth + 1));
    }

    private void serveFrontend(HttpExchange exchange, boolean head) throws IOException {
        if (!head && !exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            error(exchange, STATUS_METHOD_NOT_ALLOWED, "Method Not Allowed", false);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (!isValidPath(path)) {
            error(exchange, STATUS_NOT_FOUND, "Not Found", head);
            return;
        }

        String resource = path.equals("/") ? "index.html" : path.substring(1);
        try (InputStream input = QuestEditorHttpHandler.class.getResourceAsStream(RESOURCE_ROOT + resource)) {
            if (input == null) {
                error(exchange, STATUS_NOT_FOUND, "Not Found", head);
                return;
            }
            send(exchange, STATUS_OK, input.readAllBytes(), contentType(resource), head);
        }
    }

    private boolean isValidPath(String path) {
        if (path == null || !path.startsWith("/") || path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                return false;
            }
        }
        return true;
    }

    private boolean validOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;
        String hostname = URI.create(origin).getHost();
        String host = URI.create("http://" + exchange.getRequestHeaders().getFirst("Host")).getHost();
        return hostname != null && hostname.equalsIgnoreCase(host);
    }

    private String parameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) for (String entry : query.split("&")) {
            String[] pair = entry.split("=", 2);
            if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name)) {
                return pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            }
        }
        throw new IllegalArgumentException("Missing " + name + " parameter.");
    }

    private void assetCacheHeaders(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null && query.contains("revision=") && parameter(exchange, "revision").equals(QuestEditorAssets.revision())) {
            exchange.getResponseHeaders().set("Cache-Control", "private, max-age=86400, immutable");
        }
    }

    private String contentType(String resource) {
        String extension = resource.substring(resource.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private void error(HttpExchange exchange, int status, String message, boolean head) throws IOException {
        json(exchange, status, new ErrorResponse(message), head);
    }

    private void json(HttpExchange exchange, int status, Object body, boolean head) throws IOException {
        send(exchange, status, JSON.toJson(body).getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8", head);
    }

    private void send(HttpExchange exchange, int status, byte[] content, String type, boolean head) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (!exchange.getResponseHeaders().containsKey("Cache-Control")) exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (head) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(content.length));
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, content.length);
            exchange.getResponseBody().write(content);
        }
    }
}
