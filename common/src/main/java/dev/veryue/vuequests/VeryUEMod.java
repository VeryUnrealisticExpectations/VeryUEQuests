package dev.veryue.vuequests;

import dev.veryue.vuequests.data.FlatFileUserLoader;
import dev.veryue.vuequests.data.UserLoader;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.exceptions.PlayerDoesNotExistException;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VeryUEMod {

    public static VeryUEMod INSTANCE;

    private final Logger LOGGER = LoggerFactory.getLogger(VeryUEConstants.MOD_ID);
    private final Map<UUID, User> USERS = new ConcurrentHashMap<>();
    private UserLoader loader;
    private ScheduledExecutorService saveExecutor;
    private ScheduledFuture<?> autosave;

    public VeryUEMod() {
        INSTANCE = this;
    }

    public void start(MinecraftServer server) {
        start(new FlatFileUserLoader(server));
    }

    public void start(UserLoader userLoader) {
        Objects.requireNonNull(userLoader);
        loader = userLoader;
        saveExecutor = Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "VUEQuests user saves"));
        autosave = saveExecutor.scheduleAtFixedRate(() -> saveUsers(userLoader, List.copyOf(USERS.values())),
                5, 5, TimeUnit.MINUTES);
    }

    public void loadPlayer(UUID uuid) {
        if (saveExecutor == null) {
            LOGGER.error("Could not load quest progress for player {} because the user loader is not running", uuid);
            return;
        }

        // Queue the read behind pending writes so a quick reconnect sees the logout save.
        User user = CompletableFuture.supplyAsync(() -> readPlayer(loader, uuid), saveExecutor).join();
        if (user == null) {
            USERS.remove(uuid);
        } else {
            USERS.put(uuid, user);
        }
    }

    private User readPlayer(UserLoader activeLoader, UUID uuid) {
        try {
            return activeLoader.loadUser(uuid);
        } catch (PlayerDoesNotExistException exception) {
            return new PlayerUser(uuid);
        } catch (IllegalStateException exception) {
            LOGGER.error("Could not load quest progress for player {}", uuid, exception);
            return null;
        }
    }

    public void unloadPlayer(UUID uuid) {
        User user = USERS.remove(uuid);
        if (user != null && saveExecutor != null) {
            UserLoader activeLoader = loader;
            saveExecutor.execute(() -> saveUser(activeLoader, user));
        }
    }

    private void saveUsers(UserLoader activeLoader, Collection<User> users) {
        for (User user : users) {
            saveUser(activeLoader, user);
        }
    }

    private void saveUser(UserLoader activeLoader, User user) {
        try {
            activeLoader.saveUser(user);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not save quest progress for player {}", user.getUuid(), exception);
        }
    }

    public Optional<User> getUser(UUID uuid) {
        return Optional.ofNullable(USERS.get(uuid));
    }

    protected void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            getUser(player.getUUID()).ifPresent(user -> {
                ObjectiveContext context = ObjectiveContext.createContext(player.serverLevel())
                        .withParameter(ObjectiveContext.PLAYER, player)
                        .withParameter(ObjectiveContext.ENTITY, entity)
                        .build();
                QuestReloadDelegate.INSTANCE.getData().handleQuestTrigger(user, QuestObjectiveTypes.ENTITY, context);
            });
        }
    }

    public void onAdvancementEarned(ServerPlayer player, ResourceLocation advancement) {
        getUser(player.getUUID()).ifPresent(user -> {
            ObjectiveContext context = ObjectiveContext.createContext(player.serverLevel())
                    .withParameter(ObjectiveContext.PLAYER, player)
                    .withParameter(ObjectiveContext.ADVANCEMENT, advancement)
                    .build();
            QuestReloadDelegate.INSTANCE.getData().handleQuestTrigger(user, QuestObjectiveTypes.ADVANCEMENT, context);
        });
    }

    public void stop() {
        if (saveExecutor == null) {
            return;
        }

        autosave.cancel(false);
        UserLoader activeLoader = loader;
        CompletableFuture<Void> finalSave = CompletableFuture.runAsync(() -> saveUsers(activeLoader, USERS.values()), saveExecutor);
        saveExecutor.shutdown();
        try {
            finalSave.join();
        } finally {
            saveExecutor.close();
            USERS.clear();
            autosave = null;
            saveExecutor = null;
            loader = null;
        }
    }
}
