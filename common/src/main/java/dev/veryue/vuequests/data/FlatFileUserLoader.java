package dev.veryue.vuequests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.exceptions.PlayerDoesNotExistException;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.User;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class FlatFileUserLoader extends UserLoader {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(VeryUEConstants.MOD_ID);

    private final Path usersDirectory;

    public FlatFileUserLoader(MinecraftServer server) {
        this(server.getWorldPath(LevelResource.ROOT));
    }

    public FlatFileUserLoader(Path worldDirectory) {
        usersDirectory = worldDirectory.resolve(VeryUEConstants.MOD_ID).resolve("users");
    }

    @Override
    public User loadUser(UUID uuid) throws PlayerDoesNotExistException, IllegalStateException {
        Path path = userPath(uuid);
        try (Reader reader = Files.newBufferedReader(path)) {
            PlayerUser user = PlayerUser.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            if (!uuid.equals(user.getUuid())) {
                throw new IllegalArgumentException("Expected user UUID " + uuid + ", found " + user.getUuid());
            }
            return user;
        } catch (NoSuchFileException exception) {
            throw new PlayerDoesNotExistException(uuid, exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load user data from " + path, exception);
        }
    }

    @Override
    public boolean saveUser(User user) {
        if (!(user instanceof PlayerUser player)) {
            LOGGER.error("Player file storage does not support user type {}", user.getClass().getSimpleName());
            return false;
        }

        Path path = userPath(player.getUuid());
        try {
            JsonElement json = PlayerUser.CODEC.encodeStart(JsonOps.INSTANCE, player).getOrThrow();
            Files.createDirectories(usersDirectory);
            Path temporary = Files.createTempFile(usersDirectory, "user-", ".tmp");
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
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not save user data to {}", path, exception);
            return false;
        }
    }

    private Path userPath(UUID uuid) {
        return usersDirectory.resolve(uuid.toString() + ".json");
    }
}
