package dev.veryue.vuequests.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import dev.veryue.vuequests.config.QuestLoaderMode;
import dev.veryue.vuequests.data.quest.FlatFileQuestLoader;
import dev.veryue.vuequests.data.quest.QuestConfigStorage;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuestEditorStorage {

    public record Document(List<JsonObject> sections, Map<UUID, Identifier> sources,
                           Map<UUID, JsonObject> definitions, Set<UUID> deleted, String revision) { }
    private record State(@SerializedName("deleted_sections") Set<UUID> deletedSections) { }

    public static final String PACK_NAME = "vuequests-editor";
    public static final String PACK_ID = "file/" + PACK_NAME;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;
    private final QuestLoaderMode mode;

    public QuestEditorStorage(Path datapackDirectory) {
        this(datapackDirectory, QuestLoaderMode.DATAPACK_BASED);
    }

    public QuestEditorStorage(Path directory, QuestLoaderMode mode) {
        this.mode = mode;
        root = (mode == QuestLoaderMode.CONFIG_BASED ? directory : directory.resolve(PACK_NAME))
                .toAbsolutePath().normalize();
    }

    public Document read(ResourceManager resources) {
        try {
            Set<UUID> deleted = deletedSections();
            Map<UUID, Identifier> sources = new HashMap<>();
            Map<UUID, JsonObject> definitions = new HashMap<>();
            if (mode == QuestLoaderMode.CONFIG_BASED) {
                for (JsonObject section : new QuestConfigStorage(root).read().values()) {
                    UUID id = UUID.fromString(section.get("section_id").getAsString());
                    Identifier source = Identifier.fromNamespaceAndPath("vuequests", "vuequests/sections/" + id + ".json");
                    if (sources.putIfAbsent(id, source) != null) throw new IllegalArgumentException("Duplicate section UUID " + id);
                    definitions.put(id, section);
                }
            } else {
                var files = FileToIdConverter.json(FlatFileQuestLoader.RESOURCE_DIRECTORY).listMatchingResources(resources);
                for (var entry : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonObject section = JsonParser.parseReader(reader).getAsJsonObject();
                        UUID id = UUID.fromString(section.get("section_id").getAsString());
                        if (sources.putIfAbsent(id, entry.getKey()) != null) throw new IllegalArgumentException("Duplicate section UUID " + id);
                        definitions.put(id, section);
                    }
                }
            }
            List<JsonObject> sections = definitions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .filter(entry -> !deleted.contains(entry.getKey())).map(Map.Entry::getValue).toList();
            String revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJson(sections).getBytes(StandardCharsets.UTF_8)));
            return new Document(sections, Map.copyOf(sources), Map.copyOf(definitions), Set.copyOf(deleted), revision);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public Set<UUID> deletedSections() throws IOException {
        if (mode == QuestLoaderMode.CONFIG_BASED) {
            return Set.of();
        }
        Path state = root.resolve("editor-state.json");
        Set<UUID> deleted = new HashSet<>();
        if (Files.isRegularFile(state)) {
            deleted.addAll(JSON.fromJson(Files.readString(state), State.class).deletedSections());
        }
        return deleted;
    }

    public void save(Document previous, List<JsonObject> sections) throws IOException {
        if (mode == QuestLoaderMode.CONFIG_BASED) {
            new QuestConfigStorage(root).save(sections);
            return;
        }
        Files.createDirectories(root);
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        JsonArray version = new JsonArray();
        version.add(format.major());
        version.add(format.minor());
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "VeryUE Quests editor");
        pack.add("min_format", version);
        pack.add("max_format", version.deepCopy());
        JsonObject metadata = new JsonObject();
        metadata.add("pack", pack);
        write(root.resolve("pack.mcmeta"), metadata);

        Set<UUID> present = new HashSet<>();
        Set<Path> desired = new HashSet<>();
        for (var entry : sections) {
            JsonObject section = entry.getAsJsonObject();
            UUID id = UUID.fromString(section.get("section_id").getAsString());
            present.add(id);
            Identifier source = previous.sources().getOrDefault(id,
                    Identifier.fromNamespaceAndPath("vuequests", "vuequests/sections/editor_" + id + ".json"));
            Path target = sectionPath(source);
            write(target, section);
            desired.add(target);
        }

        Set<UUID> deleted = new HashSet<>(previous.deleted());
        deleted.addAll(previous.sources().keySet());
        deleted.removeAll(present);
        for (UUID id : deleted) {
            Identifier source = previous.sources().get(id);
            JsonObject definition = previous.definitions().get(id);
            if (source != null && definition != null) {
                // Disabled overrides prevent a deleted bundled quest section from reappearing on reload.
                JsonObject tombstone = definition.deepCopy();
                tombstone.addProperty("disabled", true);
                tombstone.add("quests", new JsonArray());
                Path target = sectionPath(source);
                write(target, tombstone);
                desired.add(target);
            }
        }
        Path data = root.resolve("data");
        if (Files.isDirectory(data)) {
            try (var files = Files.walk(data)) {
                for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).toList()) {
                    if (!desired.contains(file.toAbsolutePath().normalize())) {
                        Files.delete(file);
                    }
                }
            }
        }
        write(root.resolve("editor-state.json"), new State(new java.util.TreeSet<>(deleted)));
    }

    private Path sectionPath(Identifier location) {
        Path path = root.resolve("data").resolve(location.getNamespace()).resolve(location.getPath()).normalize();
        if (!path.startsWith(root.resolve("data")) || !location.getPath().startsWith("vuequests/sections/")
                || !location.getPath().endsWith(".json")) {
            throw new IllegalArgumentException("Invalid quest section resource " + location);
        }
        return path;
    }

    private void write(Path path, Object json) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "editor-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(json) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
