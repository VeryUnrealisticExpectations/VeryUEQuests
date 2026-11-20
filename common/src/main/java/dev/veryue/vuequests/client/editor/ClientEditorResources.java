package dev.veryue.vuequests.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.veryue.vuequests.client.lang.ClientQuestLanguages;
import dev.veryue.vuequests.client.lang.QuestLanguageCatalog;
import dev.veryue.vuequests.client.mixin.ClientLanguageAccessor;
import dev.veryue.vuequests.client.mixin.FontManagerAccessor;
import dev.veryue.vuequests.client.mixin.MinecraftAccessor;
import dev.veryue.vuequests.editor.EditorResourceSource;
import dev.veryue.vuequests.exceptions.EditorResourcesUnavailableException;
import dev.veryue.vuequests.editor.QuestEditorAssets;
import dev.veryue.vuequests.editor.QuestEditorResources;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

public class ClientEditorResources implements EditorResourceSource {
    private static final String DEFAULT_LOCALE = "en_us";
    private static final int MAX_CACHED_LOCALES = 4;
    private static ClientEditorResources instance;
    private final NativeComponentPreview renderer = new NativeComponentPreview();
    private final LinkedHashMap<String, Language> languages = new LinkedHashMap<>(8, 0.75F, true);
    private final LinkedHashMap<String, JsonObject> catalogs = new LinkedHashMap<>(8, 0.75F, true);
    private ResourceManager lastResources;
    private Language lastLanguage;
    private int optionsFingerprint;
    private String catalogRevision;

    public static void initialize() {
        instance = new ClientEditorResources();
        QuestEditorResources.install(instance);
    }

    public static void resourcesChanged() {
        QuestEditorAssets.invalidate();
        if (instance != null) {
            instance.renderer.invalidate();
        }
    }

    public static void tick(Minecraft minecraft) {
        if (instance == null) {
            return;
        }
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

    static String locale(Minecraft minecraft, String requested) {
        if (requested == null) {
            String selected = minecraft.getLanguageManager().getSelected();
            return minecraft.getLanguageManager().getLanguage(selected) == null ? DEFAULT_LOCALE : selected;
        }
        if (!DEFAULT_LOCALE.equals(requested) && minecraft.getLanguageManager().getLanguage(requested) == null) {
            throw new IllegalArgumentException("Unknown Minecraft language: " + requested);
        }
        return requested;
    }

    static Language language(Minecraft minecraft, String locale) {
        ClientEditorResources resources = instance;
        resources.checkRevision(QuestEditorAssets.revision());
        if (locale.equals(locale(minecraft, null))) {
            return Language.getInstance();
        }
        Language language = resources.languages.computeIfAbsent(locale, code -> {
            if (DEFAULT_LOCALE.equals(code)) {
                return ClientLanguage.loadFrom(minecraft.getResourceManager(), List.of(DEFAULT_LOCALE), false);
            }
            return ClientLanguage.loadFrom(minecraft.getResourceManager(), List.of(DEFAULT_LOCALE, code),
                    minecraft.getLanguageManager().getLanguage(code).bidirectional());
        });
        if (resources.languages.size() > MAX_CACHED_LOCALES) {
            resources.languages.pollFirstEntry();
        }
        return language;
    }

    private void checkRevision(String revision) {
        if (revision.equals(catalogRevision)) {
            return;
        }
        catalogs.clear();
        languages.clear();
        catalogRevision = revision;
    }

    @Override
    public CompletableFuture<JsonObject> describe(String requestedLocale) {
        return Minecraft.getInstance().submit(() -> {
            Minecraft minecraft = hostingClient();
            String locale = locale(minecraft, requestedLocale);
            String revision = QuestEditorAssets.revision();
            checkRevision(revision);
            JsonObject cached = catalogs.get(locale);
            if (cached != null) {
                return cached.deepCopy();
            }
            Language language = language(minecraft, locale);
            JsonObject response = new JsonObject();
            response.addProperty("assetRevision", revision);
            response.addProperty("source", "local-client");
            response.addProperty("language", locale);
            response.addProperty("rightToLeft", language.isDefaultRightToLeft());
            JsonArray fonts = new JsonArray();
            FontManager manager = ((MinecraftAccessor) minecraft).vuequests$getFontManager();
            ((FontManagerAccessor) manager).vuequests$getFontSets().keySet().stream().sorted().forEach(id -> {
                JsonObject font = new JsonObject();
                font.addProperty("id", id.toString());
                font.addProperty("loaded", true);
                fonts.add(font);
            });
            JsonArray translations = new JsonArray();
            if (language instanceof ClientLanguageAccessor catalog) {
                catalog.vuequests$getTranslations().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    JsonObject translation = new JsonObject();
                    translation.addProperty("key", entry.getKey());
                    translation.addProperty("value", entry.getValue());
                    translations.add(translation);
                });
            }
            response.add("fonts", fonts);
            response.add("translations", translations);
            response.add("keybinds", QuestLanguageCatalog.withLanguage(language, () -> describeKeybinds(minecraft)));
            JsonArray questBackgrounds = new JsonArray();
            minecraft.getResourceManager().listResources(QuestEditorAssets.QUEST_BACKGROUND_TEXTURE_DIRECTORY,
                            id -> id.getPath().endsWith(".png")).keySet().stream()
                    .map(QuestEditorAssets.GUI_SPRITE_FILES::fileToId).sorted()
                    .forEach(id -> questBackgrounds.add(id.toString()));
            response.add("questBackgrounds", questBackgrounds);
            catalogs.put(locale, response);
            if (catalogs.size() > MAX_CACHED_LOCALES) {
                catalogs.pollFirstEntry();
            }
            return response.deepCopy();
        });
    }

    private JsonArray describeKeybinds(Minecraft minecraft) {
        JsonArray keybinds = new JsonArray();
        for (KeyMapping key : minecraft.options.keyMappings) {
            JsonObject binding = new JsonObject();
            binding.addProperty("key", key.getName());
            binding.addProperty("label", Component.translatable(key.getName()).getString());
            binding.addProperty("resolved", key.getTranslatedKeyMessage().getString());
            keybinds.add(binding);
        }
        return keybinds;
    }

    @Override
    public CompletableFuture<JsonObject> listQuestLanguages() {
        return Minecraft.getInstance().submit(() -> ClientQuestLanguages.list(hostingClient()));
    }

    @Override
    public CompletableFuture<JsonObject> readQuestLanguage(String locale) {
        return Minecraft.getInstance().submit(() -> {
            try {
                return ClientQuestLanguages.read(hostingClient(), locale);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<JsonObject> saveQuestLanguage(String locale, JsonObject request) {
        return Minecraft.getInstance().submit(() -> {
            try {
                return ClientQuestLanguages.save(hostingClient(), locale, request);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> read(String path) {
        QuestEditorAssets.validate(path);
        return Minecraft.getInstance().submit(() -> {
            ResourceManager resources = hostingClient().getResourceManager();
            if (!path.startsWith("assets/") || path.split("/").length < 3) {
                return null;
            }
            String[] pieces = path.split("/", 3);
            Optional<Resource> resource = resources.getResource(ResourceLocation.fromNamespaceAndPath(pieces[1], pieces[2]));
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream input = resource.get().open()) {
                byte[] bytes = input.readNBytes(8 * 1024 * 1024 + 1);
                if (bytes.length > 8 * 1024 * 1024) {
                    throw new IOException("The resource exceeds the editor's 8 MiB limit.");
                }
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
            if (path.isEmpty()) {
                return List.of("assets");
            }
            if (path.equals("assets")) {
                return resources.getNamespaces().stream().sorted().toList();
            }
            String[] pieces = path.split("/", 3);
            if (pieces.length < 2) {
                return List.of();
            }
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
