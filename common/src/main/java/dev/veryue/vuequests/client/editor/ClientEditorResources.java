package dev.veryue.vuequests.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.veryue.vuequests.client.mixin.ClientLanguageAccessor;
import dev.veryue.vuequests.client.mixin.FontManagerAccessor;
import dev.veryue.vuequests.client.mixin.MinecraftAccessor;
import dev.veryue.vuequests.editor.EditorResourceSource;
import dev.veryue.vuequests.editor.EditorResourcesUnavailableException;
import dev.veryue.vuequests.editor.QuestEditorAssets;
import dev.veryue.vuequests.editor.QuestEditorResources;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

public class ClientEditorResources implements EditorResourceSource {
    private static ClientEditorResources instance;
    private final NativeComponentPreview renderer = new NativeComponentPreview();
    private ResourceManager lastResources;
    private Language lastLanguage;
    private int optionsFingerprint;
    private JsonObject catalog;
    private String catalogRevision;

    public static void initialize() {
        instance = new ClientEditorResources();
        QuestEditorResources.install(instance);
    }

    public static void resourcesChanged() {
        QuestEditorAssets.invalidate();
        if (instance != null) {
            instance.catalog = null;
            instance.renderer.invalidate();
        }
    }

    public static void tick(Minecraft minecraft) {
        if (instance == null) return;
        int fingerprint = Objects.hash(minecraft.options.languageCode, minecraft.options.forceUnicodeFont().get(), minecraft.options.japaneseGlyphVariants().get());
        for (KeyMapping key : minecraft.options.keyMappings) fingerprint = fingerprint * 31 + key.saveString().hashCode();
        if (instance.lastResources != minecraft.getResourceManager() || instance.lastLanguage != Language.getInstance() || fingerprint != instance.optionsFingerprint) {
            instance.lastResources = minecraft.getResourceManager();
            instance.lastLanguage = Language.getInstance();
            instance.optionsFingerprint = fingerprint;
            resourcesChanged();
        }
    }

    private static Minecraft hostingClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null || !QuestEditorResources.hasLocalClient()) {
            throw new EditorResourcesUnavailableException("The client hosting this world is unavailable.");
        }
        return minecraft;
    }

    @Override
    public CompletableFuture<JsonObject> describe() {
        return Minecraft.getInstance().submit(() -> {
            Minecraft minecraft = hostingClient();
            String revision = QuestEditorAssets.revision();
            if (catalog != null && revision.equals(catalogRevision)) return catalog.deepCopy();
            JsonObject response = new JsonObject();
            response.addProperty("assetRevision", revision);
            response.addProperty("source", "local-client");
            response.addProperty("language", minecraft.options.languageCode);
            response.addProperty("rightToLeft", Language.getInstance().isDefaultRightToLeft());
            JsonArray fonts = new JsonArray();
            var manager = ((MinecraftAccessor) minecraft).vuequests$getFontManager();
            ((FontManagerAccessor) manager).vuequests$getFontSets().keySet().stream().sorted().forEach(id -> {
                JsonObject font = new JsonObject();
                font.addProperty("id", id.toString());
                font.addProperty("loaded", true);
                fonts.add(font);
            });
            JsonArray translations = new JsonArray();
            if (Language.getInstance() instanceof ClientLanguageAccessor language) {
                language.vuequests$getTranslations().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    JsonObject translation = new JsonObject();
                    translation.addProperty("key", entry.getKey());
                    translation.addProperty("value", entry.getValue());
                    translations.add(translation);
                });
            }
            JsonArray keybinds = new JsonArray();
            for (KeyMapping key : minecraft.options.keyMappings) {
                JsonObject binding = new JsonObject();
                binding.addProperty("key", key.getName());
                binding.addProperty("label", Component.translatable(key.getName()).getString());
                binding.addProperty("resolved", key.getTranslatedKeyMessage().getString());
                keybinds.add(binding);
            }
            response.add("fonts", fonts);
            response.add("translations", translations);
            response.add("keybinds", keybinds);
            catalog = response;
            catalogRevision = revision;
            return response.deepCopy();
        });
    }

    @Override
    public CompletableFuture<byte[]> read(String path) {
        QuestEditorAssets.validate(path);
        return Minecraft.getInstance().submit(() -> {
            ResourceManager resources = hostingClient().getResourceManager();
            if (!path.startsWith("assets/") || path.split("/").length < 3) return null;
            String[] pieces = path.split("/", 3);
            var resource = resources.getResource(ResourceLocation.fromNamespaceAndPath(pieces[1], pieces[2]));
            if (resource.isEmpty()) return null;
            try (var input = resource.get().open()) {
                byte[] bytes = input.readNBytes(8 * 1024 * 1024 + 1);
                if (bytes.length > 8 * 1024 * 1024) throw new IOException("The resource exceeds the editor's 8 MiB limit.");
                return bytes;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> list(String path) {
        QuestEditorAssets.validate(path);
        return Minecraft.getInstance().submit(() -> {
            ResourceManager resources = hostingClient().getResourceManager();
            if (path.isEmpty()) return List.of("assets");
            if (path.equals("assets")) return resources.getNamespaces().stream().sorted().toList();
            String[] pieces = path.split("/", 3);
            if (pieces.length < 2) return List.of();
            String prefix = pieces.length == 2 ? "" : pieces[2] + "/";
            TreeSet<String> names = new TreeSet<>();
            resources.listResources(pieces.length == 2 ? "" : pieces[2], id -> id.getNamespace().equals(pieces[1]) && id.getPath().startsWith(prefix)).keySet().forEach(id -> {
                String remainder = id.getPath().substring(prefix.length());
                int separator = remainder.indexOf('/');
                names.add(separator < 0 ? remainder : remainder.substring(0, separator));
            });
            return List.copyOf(names);
        });
    }

    @Override
    public CompletableFuture<JsonObject> preview(JsonObject request) { return renderer.request(request); }
}
