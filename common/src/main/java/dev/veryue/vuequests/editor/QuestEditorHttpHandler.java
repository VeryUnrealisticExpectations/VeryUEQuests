package dev.veryue.vuequests.editor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public class QuestEditorHttpHandler implements HttpHandler {

    public static final String RESOURCE_ROOT = "/web/vuequests/";

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("map", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("ogg", "audio/ogg")
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            boolean head = exchange.getRequestMethod().equals("HEAD");
            if (!head && !exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                sendError(exchange, 405, "Method Not Allowed", false);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (!isValidPath(path)) {
                sendError(exchange, 404, "Not Found", head);
                return;
            }

            String resource = path.equals("/") ? "index.html" : path.substring(1);
            try (InputStream input = QuestEditorHttpHandler.class.getResourceAsStream(RESOURCE_ROOT + resource)) {
                if (input == null) {
                    sendError(exchange, 404, "Not Found", head);
                    return;
                }

                byte[] content = input.readAllBytes();
                String extension = resource.substring(resource.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                sendResponse(exchange, 200, content, head);
            }
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

    private void sendError(HttpExchange exchange, int status, String message, boolean head) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        sendResponse(exchange, status, message.getBytes(StandardCharsets.UTF_8), head);
    }

    private void sendResponse(HttpExchange exchange, int status, byte[] content, boolean head) throws IOException {
        if (head) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(content.length));
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, content.length);
            exchange.getResponseBody().write(content);
        }
    }
}
