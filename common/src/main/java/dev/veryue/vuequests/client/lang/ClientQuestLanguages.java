package dev.veryue.vuequests.client.lang;

import com.google.common.hash.Hashing;
import com.google.gson.*;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.editor.QuestEditorService.ConflictException;
import dev.veryue.vuequests.exceptions.EditorResourcesUnavailableException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class ClientQuestLanguages {
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DIRECTORY = "vuequests_lang";
    private static final String ENGLISH = "en_us";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Pattern LOCALE = Pattern.compile("[a-z0-9_.-]+");
    private static Path directory;

    public static void initialize(Path configDirectory) {
        directory = configDirectory.resolve(DIRECTORY);
    }

    public static QuestData buildTranslations(Minecraft minecraft, QuestData data) {
        if (minecraft == null || minecraft.level == null || directory == null || data == QuestData.EMPTY) {
            return data;
        }
        LanguageManager languageManager = minecraft.getLanguageManager();
        String selected = languageManager.getSelected();
        String locale = languageManager.getLanguage(selected) != null && isLocaleFilename(selected) ? selected : ENGLISH;
        DynamicOps<JsonElement> ops = minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<PackResources> packs = minecraft.getResourceManager().listPacks().toList();
        QuestLanguageCatalog catalog = loadLocale(packs, ENGLISH, ops);
        if (!ENGLISH.equals(locale)) {
            catalog.overlay(loadLocale(packs, locale, ops));
        }
        return catalog.buildView(data);
    }

    public static JsonObject list(Minecraft minecraft) {
        List<PackResources> packs = minecraft.getResourceManager().listPacks().toList();
        JsonArray languages = new JsonArray();
        minecraft.getLanguageManager().getLanguages().forEach((code, info) -> {
            if (!isLocaleFilename(code)) {
                return;
            }
            ResourceLocation id = resourceId(code);
            JsonObject language = new JsonObject();
            language.addProperty("code", code);
            language.addProperty("name", info.toComponent().getString());
            language.addProperty("hasCatalog", Files.isRegularFile(configFile(code)) || packs.stream()
                    .anyMatch(pack -> pack.getResource(PackType.CLIENT_RESOURCES, id) != null));
            languages.add(language);
        });
        JsonObject result = new JsonObject();
        result.add("languages", languages);
        return result;
    }

    public static JsonObject read(Minecraft minecraft, String locale) throws IOException {
        requireLocale(minecraft, locale);
        DynamicOps<JsonElement> ops = editorOps(minecraft);
        QuestLanguagePackCatalogs packs = loadPackCatalogs(minecraft.getResourceManager().listPacks().toList(), locale, ops);
        byte[] local = readLocal(locale);
        return snapshot(locale, packs, decode(local, ops), local);
    }

    public static JsonObject save(Minecraft minecraft, String locale, JsonObject request) throws IOException {
        requireLocale(minecraft, locale);
        DynamicOps<JsonElement> ops = editorOps(minecraft);
        QuestLanguagePackCatalogs packs = loadPackCatalogs(minecraft.getResourceManager().listPacks().toList(), locale, ops);
        byte[] local = readLocal(locale);
        JsonElement revision = request.get("revision");
        if (revision == null || !revision.isJsonPrimitive() || !revision.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Supply the quest language catalog revision.");
        }
        if (!revision(packs, local).equals(revision.getAsString())) {
            throw new ConflictException("The quest language catalog changed since it was loaded. Reload before saving.");
        }
        QuestLanguageCatalog overrides = QuestLanguageCatalog.decode(request.get("overrides"), ops);
        byte[] bytes = (JSON.toJson(overrides.toJson()) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("The quest language catalog exceeds 8 MiB.");
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".quest-language-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, configFile(locale), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        ClientQuestData.refreshTranslations();
        return snapshot(locale, packs, overrides, bytes);
    }

    private static DynamicOps<JsonElement> editorOps(Minecraft minecraft) {
        if (minecraft.level == null) {
            throw new EditorResourcesUnavailableException("The local Minecraft world is unavailable.");
        }
        return minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private static JsonObject snapshot(String locale, QuestLanguagePackCatalogs packs, QuestLanguageCatalog overrides, byte[] local) {
        JsonObject result = new JsonObject();
        result.addProperty("locale", locale);
        result.add("assets", packs.assets().toJson());
        result.add("overrides", overrides.toJson());
        result.add("server", packs.server().toJson());
        result.addProperty("revision", revision(packs, local));
        return result;
    }

    private static String revision(QuestLanguagePackCatalogs packs, byte[] local) {
        return Hashing.sha256().newHasher().putString(packs.assets().toJson().toString(), StandardCharsets.UTF_8)
                .putByte((byte) 0).putString(packs.server().toJson().toString(), StandardCharsets.UTF_8)
                .putByte((byte) 0).putBytes(local == null ? new byte[0] : local).hash().toString();
    }

    private static QuestLanguageCatalog loadLocale(List<PackResources> packs, String locale, DynamicOps<JsonElement> ops) {
        QuestLanguagePackCatalogs layers = loadPackCatalogs(packs, locale, ops);
        QuestLanguageCatalog catalog = layers.assets();
        try {
            catalog.overlay(decode(readLocal(locale), ops));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Ignoring invalid quest language config {}: {}", configFile(locale), exception.getMessage());
        }
        catalog.overlay(layers.server());
        return catalog;
    }

    private static QuestLanguagePackCatalogs loadPackCatalogs(List<PackResources> packs, String locale, DynamicOps<JsonElement> ops) {
        QuestLanguageCatalog assets = new QuestLanguageCatalog();
        QuestLanguageCatalog server = new QuestLanguageCatalog();
        ResourceLocation id = resourceId(locale);
        // A catalog in a higher-priority pack supplies only the fields it defines.
        for (PackResources pack : packs) {
            IoSupplier<InputStream> resource = pack.getResource(PackType.CLIENT_RESOURCES, id);
            if (resource == null) {
                continue;
            }
            try (InputStream stream = resource.get()) {
                QuestLanguageCatalog catalog = pack.location().source() == PackSource.SERVER ? server : assets;
                catalog.overlay(decode(readBytes(stream), ops));
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Ignoring invalid quest language asset {} from {}: {}", id, pack.packId(), exception.getMessage());
            }
        }
        return new QuestLanguagePackCatalogs(assets, server);
    }

    private static byte[] readLocal(String locale) throws IOException {
        try (InputStream stream = Files.newInputStream(configFile(locale))) {
            return readBytes(stream);
        } catch (NoSuchFileException missing) {
            return null;
        }
    }

    private static byte[] readBytes(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("The quest language catalog exceeds 8 MiB.");
        }
        return bytes;
    }

    private static QuestLanguageCatalog decode(byte[] bytes, DynamicOps<JsonElement> ops) {
        return bytes == null ? new QuestLanguageCatalog() : QuestLanguageCatalog.decode(
                JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)), ops);
    }

    private static void requireLocale(Minecraft minecraft, String locale) {
        if (!isLocaleFilename(locale) || !ENGLISH.equals(locale) && !minecraft.getLanguageManager().getLanguages().containsKey(locale)) {
            throw new IllegalArgumentException("Select a language registered in Minecraft.");
        }
    }

    private static boolean isLocaleFilename(String locale) {
        return locale != null && LOCALE.matcher(locale).matches() && !locale.equals(".") && !locale.equals("..");
    }

    private static ResourceLocation resourceId(String locale) {
        return ResourceLocation.fromNamespaceAndPath(DIRECTORY, locale + ".json");
    }

    private static Path configFile(String locale) {
        return directory.resolve(locale + ".json");
    }

    private record QuestLanguagePackCatalogs(QuestLanguageCatalog assets, QuestLanguageCatalog server) { }
}
