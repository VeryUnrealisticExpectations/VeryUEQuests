package dev.veryue.vuequests.config;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public record ServerConfig(QuestView questView, QuestLoaderMode questLoader) {

    public static final ServerConfig DEFAULT = new ServerConfig(QuestView.LIST, QuestLoaderMode.DATAPACK_BASED);
    public static final Codec<ServerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            QuestView.CODEC.fieldOf("quest_view").forGetter(ServerConfig::questView),
            QuestLoaderMode.CODEC.fieldOf("quest_loader").forGetter(ServerConfig::questLoader)
    ).apply(instance, ServerConfig::new));
    private static final Map<String, String> COMMENTS = Map.of(
            "quest_view", "Quest menu layout for all players. Allowed values: \"list\" or \"graph\".",
            "quest_loader", "Quest source. Allowed values: \"DATAPACK_BASED\" or \"CONFIG_BASED\". "
                    + "Modpacks should run /vuequests export, then use \"CONFIG_BASED\" to distribute quests in config/vuequests/.");

    public static ServerConfig load(Path file) {
        Path path = file.toAbsolutePath().normalize();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                String defaults = CommentedJsonWriter.write(CODEC.encodeStart(JsonOps.INSTANCE, DEFAULT).getOrThrow(), COMMENTS);
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
