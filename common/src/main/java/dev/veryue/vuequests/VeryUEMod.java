package dev.veryue.vuequests;

import dev.veryue.vuequests.data.FlatFileUserLoader;
import dev.veryue.vuequests.data.UserLoader;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.exceptions.PlayerDoesNotExistException;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import net.minecraft.core.GlobalPos;
import net.minecraft.stats.Stat;
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
    private final Map<UUID, GlobalPos> lastTravelPositions = new HashMap<>();
    private final Map<UUID, Integer> lastExperienceLevels = new HashMap<>();
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
        lastTravelPositions.remove(uuid);
        lastExperienceLevels.remove(uuid);
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

    protected void onPlayerLogin(ServerPlayer player) {
        loadPlayer(player.getUUID());
        restoreAttributeRewards(player);
        checkPlayerObjectives(player);
    }

    protected void restoreAttributeRewards(ServerPlayer player) {
        getUser(player.getUUID()).ifPresent(user -> QuestReloadDelegate.INSTANCE.getData().restoreAttributeRewards(user, player));
    }

    protected void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            getUser(player.getUUID()).ifPresent(user -> {
                ObjectiveContext context = ObjectiveContext.createContext(player.serverLevel())
                        .withParameter(ObjectiveContext.PLAYER, player)
                        .withParameter(ObjectiveContext.ENTITY, entity)
                        .build();
                if (QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, QuestObjectiveTypes.ENTITY, context)) {
                    syncProgress(player);
                }
            });
        }
    }

    public void onAdvancementEarned(ServerPlayer player, ResourceLocation advancement) {
        getUser(player.getUUID()).ifPresent(user -> {
            ObjectiveContext context = ObjectiveContext.createContext(player.serverLevel())
                    .withParameter(ObjectiveContext.PLAYER, player)
                    .withParameter(ObjectiveContext.ADVANCEMENT, advancement)
                    .build();
            if (QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, QuestObjectiveTypes.ADVANCEMENT, context)) {
                syncProgress(player);
            }
        });
    }

    protected void syncProgress(ServerPlayer player) {
    }

    public void onPlayerMoved(ServerPlayer player) {
        GlobalPos position = GlobalPos.of(player.level().dimension(), player.blockPosition());
        if (!position.equals(lastTravelPositions.put(player.getUUID(), position))) {
            triggerPlayerObjectives(player, QuestObjectiveTypes.TRAVEL_DIMENSION, QuestObjectiveTypes.TRAVEL_BIOME,
                    QuestObjectiveTypes.TRAVEL_COORDINATES, QuestObjectiveTypes.TRAVEL_STRUCTURE);
        }
    }

    public void onExperienceChanged(ServerPlayer player) {
        int level = player.experienceLevel;
        Integer previousLevel = lastExperienceLevels.put(player.getUUID(), level);
        if (previousLevel == null || previousLevel != level) {
            triggerPlayerObjectives(player, QuestObjectiveTypes.EXPERIENCE_LEVELS);
        }
    }

    public void onStatisticChanged(ServerPlayer player, Stat<?> statistic) {
        getUser(player.getUUID()).ifPresent(user -> {
            ObjectiveContext context = ObjectiveContext.createContext(player.serverLevel())
                    .withParameter(ObjectiveContext.PLAYER, player).withParameter(ObjectiveContext.STATISTIC, statistic).build();
            if (QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, QuestObjectiveTypes.STATISTIC, context)) {
                syncProgress(player);
            }
        });
    }

    public void checkPlayerObjectives(ServerPlayer player) {
        // todo; maybe split this out if it's not very performant
        lastTravelPositions.put(player.getUUID(), GlobalPos.of(player.level().dimension(), player.blockPosition()));
        lastExperienceLevels.put(player.getUUID(), player.experienceLevel);
        triggerPlayerObjectives(player, QuestObjectiveTypes.COLLECT_ITEM, QuestObjectiveTypes.ADVANCEMENT,
                QuestObjectiveTypes.EXPERIENCE_LEVELS, QuestObjectiveTypes.STATISTIC, QuestObjectiveTypes.TRAVEL_DIMENSION,
                QuestObjectiveTypes.TRAVEL_BIOME, QuestObjectiveTypes.TRAVEL_COORDINATES, QuestObjectiveTypes.TRAVEL_STRUCTURE);
    }

    private void triggerPlayerObjectives(ServerPlayer player, QuestObjectiveSerializer<?>... types) {
        getUser(player.getUUID()).ifPresent(user -> {
            ObjectiveContext context = ObjectiveContext.createContext(player.serverLevel()).withParameter(ObjectiveContext.PLAYER, player).build();
            boolean changed = false;
            for (QuestObjectiveSerializer<?> type : types) {
                changed |= QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, type, context);
            }
            if (changed) {
                syncProgress(player);
            }
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
            lastTravelPositions.clear();
            lastExperienceLevels.clear();
            autosave = null;
            saveExecutor = null;
            loader = null;
        }
    }
}
