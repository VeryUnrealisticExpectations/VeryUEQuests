package dev.veryue.vuequests.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.veryue.vuequests.exceptions.EditorResourcesUnavailableException;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class QuestEditorResources {
    private static volatile EditorResourceSource clientSource;
    private static volatile MinecraftServer server;

    public static void install(EditorResourceSource source) {
        clientSource = source;
        QuestEditorAssets.invalidate();
    }

    public static void start(MinecraftServer hostingServer) { server = hostingServer; }

    public static void stop() { server = null; }

    public static boolean hasLocalClient() { return source() != null; }

    private static EditorResourceSource source() {
        MinecraftServer hostingServer = server;
        return hostingServer != null && !hostingServer.isDedicatedServer() ? clientSource : null;
    }

    public static CompletableFuture<JsonObject> describe() {
        EditorResourceSource source = source();
        if (source != null) return source.describe();
        try {
            return CompletableFuture.completedFuture(describeAssets());
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static CompletableFuture<byte[]> read(String path) {
        EditorResourceSource source = source();
        if (source != null) return source.read(path);
        try {
            return CompletableFuture.completedFuture(QuestEditorAssets.read(path));
        } catch (IOException | IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static CompletableFuture<List<String>> list(String path) {
        EditorResourceSource source = source();
        if (source != null) return source.list(path);
        try {
            return CompletableFuture.completedFuture(QuestEditorAssets.list(path));
        } catch (IOException | IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static CompletableFuture<JsonObject> preview(JsonObject request) {
        EditorResourceSource source = source();
        if (source == null) return CompletableFuture.failedFuture(new EditorResourcesUnavailableException("Native text rendering requires the local Minecraft client hosting this world."));
        return source.preview(request);
    }

    private static JsonObject describeAssets() throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("assetRevision", QuestEditorAssets.revision());
        result.addProperty("source", "server-assets");
        result.addProperty("language", "en_us");
        result.addProperty("rightToLeft", false);
        JsonArray fonts = new JsonArray();
        JsonArray translations = new JsonArray();
        for (String namespace : QuestEditorAssets.list("assets")) {
            ArrayDeque<String> paths = new ArrayDeque<>();
            paths.add("assets/" + namespace + "/font");
            int scanned = 0;
            while (!paths.isEmpty() && scanned++ < 2048) {
                String path = paths.removeFirst();
                for (String name : QuestEditorAssets.list(path)) {
                    String child = path + "/" + name;
                    if (name.endsWith(".json")) {
                        byte[] bytes = QuestEditorAssets.read(child);
                        if (bytes == null) continue;
                        JsonObject font = new JsonObject();
                        font.addProperty("id", namespace + ":" + child.substring(("assets/" + namespace + "/font/").length(), child.length() - 5));
                        font.addProperty("loaded", false);
                        try {
                            font.add("definition", JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)));
                        } catch (RuntimeException ignored) { }
                        fonts.add(font);
                    } else if (!name.contains(".")) paths.add(child);
                }
            }
            byte[] language = QuestEditorAssets.read("assets/" + namespace + "/lang/en_us.json");
            if (language != null) {
                try {
                    JsonParser.parseString(new String(language, StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(entry -> {
                        if (!entry.getValue().isJsonPrimitive()) return;
                        JsonObject translation = new JsonObject();
                        translation.addProperty("key", entry.getKey());
                        translation.addProperty("value", entry.getValue().getAsString());
                        translations.add(translation);
                    });
                } catch (RuntimeException ignored) { }
            }
        }
        result.add("fonts", fonts);
        result.add("translations", translations);
        result.add("keybinds", new JsonArray());
        JsonArray questBackgrounds = new JsonArray();
        QuestEditorAssets.questBackgrounds().forEach(id -> questBackgrounds.add(id.toString()));
        result.add("questBackgrounds", questBackgrounds);
        return result;
    }
}
