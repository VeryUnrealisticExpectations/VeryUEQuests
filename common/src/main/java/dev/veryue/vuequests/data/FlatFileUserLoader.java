package dev.veryue.vuequests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.exceptions.UserDoesNotExitException;
import dev.veryue.vuequests.models.User;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class FlatFileUserLoader<T extends User> extends UserLoader<T> {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    private final Codec<T> codec;
    private final Supplier<DynamicOps<JsonElement>> ops;

    public FlatFileUserLoader(MinecraftServer server, String folderName, Codec<T> codec) {
        this(server.getWorldPath(LevelResource.ROOT), folderName, codec,
                () -> server.registryAccess().createSerializationContext(JsonOps.INSTANCE));
    }

    public FlatFileUserLoader(Path worldDirectory, String folderName, Codec<T> codec) {
        this(worldDirectory, folderName, codec, () -> JsonOps.INSTANCE);
    }

    public FlatFileUserLoader(Path worldDirectory, String folderName, Codec<T> codec, Supplier<DynamicOps<JsonElement>> ops) {
        directory = worldDirectory.resolve(VeryUEConstants.MOD_ID).resolve(folderName);
        this.codec = codec;
        this.ops = ops;
    }

    @Override
    public T loadUser(UUID uuid) throws UserDoesNotExitException {
        Path path = userPath(uuid);
        try (Reader reader = Files.newBufferedReader(path)) {
            T user = codec.parse(ops.get(), JsonParser.parseReader(reader)).getOrThrow();
            if (!uuid.equals(user.getUuid())) {
                throw new IllegalArgumentException("Expected UUID " + uuid + ", found " + user.getUuid());
            }
            return user;
        } catch (NoSuchFileException exception) {
            throw new UserDoesNotExitException(uuid, exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load quest data for " + uuid + " from " + path, exception);
        }
    }

    @Override
    public void saveUser(T user) {
        Path path = userPath(user.getUuid());
        try {
            JsonElement json = codec.encodeStart(ops.get(), user).getOrThrow();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "user-", ".tmp");
            try {
                Files.writeString(temporary, JSON.toJson(json) + "\n");
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not save quest data for " + user.getUuid() + " to " + path, exception);
        }
    }

    @Override
    public void deleteUser(UUID uuid) {
        try {
            Files.delete(userPath(uuid));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not delete quest data for " + uuid + " at " + userPath(uuid), exception);
        }
    }

    @Override
    public Map<UUID, T> loadAllUsers() {
        Map<UUID, T> users = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                UUID uuid = UUID.fromString(name.substring(0, name.length() - 5));
                T user = loadUser(uuid);
                if (users.putIfAbsent(user.getUuid(), user) != null) {
                    throw new IllegalStateException("Duplicate quest data UUID " + uuid + " at " + file);
                }
            }
        } catch (NoSuchFileException exception) {
            return users;
        } catch (IOException | UserDoesNotExitException | RuntimeException exception) {
            throw new IllegalStateException("Could not enumerate quest data in " + directory, exception);
        }
        return users;
    }

    @Override
    public Path userPath(UUID uuid) {
        return directory.resolve(uuid + ".json");
    }
}
