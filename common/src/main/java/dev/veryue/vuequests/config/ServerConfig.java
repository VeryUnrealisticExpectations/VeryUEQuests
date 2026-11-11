package dev.veryue.vuequests.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record ServerConfig(QuestView questView) {

    public static final ServerConfig DEFAULT = new ServerConfig(QuestView.LIST);
    public static final Codec<ServerConfig> CODEC = QuestView.CODEC.fieldOf("quest_view")
            .xmap(ServerConfig::new, ServerConfig::questView).codec();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static ServerConfig load(Path file) {
        Path path = file.toAbsolutePath().normalize();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                String defaults = JSON.toJson(CODEC.encodeStart(JsonOps.INSTANCE, DEFAULT).getOrThrow());
                Files.writeString(path, defaults + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                return DEFAULT;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load VUE Quests server config " + path, exception);
        }
    }
}
