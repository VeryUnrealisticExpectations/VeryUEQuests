package dev.veryue.vuequests;

import dev.veryue.vuequests.data.FlatFileUserLoader;
import dev.veryue.vuequests.data.UserLoader;
import dev.veryue.vuequests.exceptions.PlayerDoesNotExistException;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.User;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VeryUEMod {

    private final Logger LOGGER = LoggerFactory.getLogger(VeryUEConstants.MOD_ID);
    private final Map<UUID, User> USERS = new HashMap<>();
    private UserLoader loader;

    public void start(MinecraftServer server) {
        start(new FlatFileUserLoader(server));
    }

    public void start(UserLoader userLoader) {
        USERS.clear();
        loader = userLoader;
    }

    public void loadPlayer(UUID uuid) {
        User user;
        try {
            if (loader == null) {
                throw new IllegalStateException("User loader has not been initialized");
            }
            user = loader.loadUser(uuid);
        } catch (PlayerDoesNotExistException exception) {
            user = new PlayerUser(uuid);
        } catch (IllegalStateException exception) {
            USERS.remove(uuid);
            LOGGER.error("Could not load quest progress for player {}", uuid, exception);
            return;
        }
        USERS.put(uuid, user);
    }

    public Optional<User> getUser(UUID uuid) {
        return Optional.ofNullable(USERS.get(uuid));
    }

    public void stop() {
        USERS.clear();
        loader = null;
    }
}
