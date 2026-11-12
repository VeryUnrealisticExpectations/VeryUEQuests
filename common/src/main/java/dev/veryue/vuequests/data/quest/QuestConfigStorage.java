package dev.veryue.vuequests.data.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.core.HolderLookup;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuestConfigStorage {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;

    public QuestConfigStorage(Path directory) {
        root = directory.toAbsolutePath().normalize();
    }

    public Map<Path, JsonObject> read() {
        Map<Path, JsonObject> sections = new LinkedHashMap<>();
        try {
            Files.createDirectories(root);
            try (var files = Files.walk(root)) {
                for (Path path : files.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".json")).sorted().toList()) {
                    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        sections.put(path, JsonParser.parseReader(reader).getAsJsonObject());
                    } catch (IOException | RuntimeException exception) {
                        throw new IllegalStateException("Could not read quest section " + path, exception);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read quest config directory " + root, exception);
        }
        return sections;
    }

    public void export(QuestData data, HolderLookup.Provider registryLookup) throws IOException {
        var ops = registryLookup.createSerializationContext(JsonOps.INSTANCE);
        List<JsonObject> sections = data.sections().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> Section.CODEC.encodeStart(ops, entry.getValue()).getOrThrow().getAsJsonObject()).toList();
        writeSections(sections, false);
    }

    public void save(List<JsonObject> sections) throws IOException {
        writeSections(sections, true);
    }

    private void writeSections(List<JsonObject> sections, boolean replace) throws IOException {
        Map<UUID, Path> existing = new HashMap<>();
        for (var entry : read().entrySet()) {
            UUID id = sectionId(entry.getValue());
            if (existing.putIfAbsent(id, entry.getKey()) != null) {
                throw new IllegalArgumentException("Duplicate section UUID " + id + " in " + root);
            }
        }
        Map<Path, JsonObject> desired = new LinkedHashMap<>();
        Set<Path> reserved = new HashSet<>(existing.values());
        Set<UUID> ids = new HashSet<>();
        for (JsonObject section : sections) {
            UUID id = sectionId(section);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate section UUID " + id);
            }
            Path target = existing.get(id);
            if (target == null) {
                target = root.resolve(id + ".json");
                int suffix = 1;
                while (!reserved.add(target)) {
                    target = root.resolve(id + "_" + suffix++ + ".json");
                }
            }
            desired.put(target, section);
        }
        for (var entry : desired.entrySet()) {
            write(entry.getKey(), entry.getValue());
        }
        if (replace) {
            for (Path path : existing.values()) {
                if (!desired.containsKey(path)) {
                    Files.delete(path);
                }
            }
        }
    }

    private UUID sectionId(JsonObject section) {
        return UUID.fromString(section.get("section_id").getAsString());
    }

    private void write(Path path, JsonObject section) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "quest-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(section) + "\n", StandardCharsets.UTF_8);
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
