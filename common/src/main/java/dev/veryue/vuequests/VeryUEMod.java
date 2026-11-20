package dev.veryue.vuequests;

import dev.veryue.vuequests.config.QuestLoaderMode;
import dev.veryue.vuequests.config.ServerConfig;
import dev.veryue.vuequests.data.quest.ConfigQuestLoader;
import dev.veryue.vuequests.data.quest.FlatFileQuestLoader;
import dev.veryue.vuequests.data.quest.QuestLoader;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import dev.veryue.vuequests.network.*;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.nio.file.Path;
import java.util.*;

public abstract class VeryUEMod {

    public static VeryUEMod INSTANCE;

    private MinecraftServer server;
    private UserManager userManager;
    private final Map<UUID, GlobalPos> lastTravelPositions = new HashMap<>();
    private final Map<UUID, Integer> lastExperienceLevels = new HashMap<>();
    private ServerConfig serverConfig = ServerConfig.DEFAULT;
    private Path serverConfigPath;

    public VeryUEMod() {
        INSTANCE = this;
    }

    public void start(MinecraftServer server) {
        this.server = server;
        userManager = new UserManager(server);
    }

    protected void loadServerConfig(Path file) {
        serverConfig = ServerConfig.load(file);
        serverConfigPath = file.toAbsolutePath().normalize();
        QuestReloadDelegate.INSTANCE.setLoader(createQuestLoader(serverConfig));
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public Path getQuestConfigDirectory() {
        if (serverConfigPath == null) {
            throw new IllegalStateException("The VUE Quests server config has not been loaded yet");
        }
        return serverConfigPath.getParent().resolve(VeryUEConstants.MOD_ID);
    }

    private QuestLoader createQuestLoader(ServerConfig config) {
        return config.questLoader() == QuestLoaderMode.CONFIG_BASED
                ? new ConfigQuestLoader(getQuestConfigDirectory()) : new FlatFileQuestLoader();
    }

    public void reloadServerConfig(MinecraftServer server) {
        if (serverConfigPath == null) {
            throw new IllegalStateException("The VUE Quests server config has not been loaded yet");
        }
        ServerConfig loaded = ServerConfig.load(serverConfigPath);
        QuestReloadDelegate.INSTANCE.reload(createQuestLoader(loaded), server.getResourceManager(), server.registryAccess());
        serverConfig = loaded;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncServerConfig(player);
            restoreAttributeRewards(player);
            syncQuests(player);
        }
    }

    protected abstract void syncServerConfig(ServerPlayer player);

    protected abstract void sendPayload(ServerPlayer player, CustomPacketPayload payload);

    protected void syncQuests(ServerPlayer player) {
        syncTeamState(player);
        if (getUserManager().isReady(player.getUUID())) {
            sendPayload(player, QuestSyncPayload.create(player));
        }
    }

    protected void syncQuestScreen(ServerPlayer player, boolean supportsProgressSync) {
        if (supportsProgressSync) {
            syncTeamState(player);
            sendProgress(player);
        } else {
            syncQuests(player);
        }
    }

    public void syncTeamState(ServerPlayer player) {
        UserManager manager = getUserManager();
        if (manager.hasFatalFailure()) {
            return;
        }
        boolean ready = manager.isReady(player.getUUID());
        sendPayload(player, new PersonalDataStatusPayload(ready, ready ? Component.empty() : manager.dataStatus(player.getUUID())));
        sendPayload(player, TeamSyncPayload.create(player));
    }

    public void handleTeamRequest(ServerPlayer player, TeamRequestPayload request) {
        UserManager manager = getUserManager();
        try {
            Set<UUID> affected = request.apply(manager, player);
            refreshUsers(affected);
            for (UUID id : affected) {
                ServerPlayer member = player.level().getServer().getPlayerList().getPlayer(id);
                if (member != null) {
                    syncTeamState(member);
                }
            }
            sendPayload(player, new TeamActionResultPayload(request.requestId(), true, Component.translatable("team.vuequests.action_success")));
        } catch (TeamActionException exception) {
            for (ServerPlayer member : player.level().getServer().getPlayerList().getPlayers()) syncTeamState(member);
            sendPayload(player, new TeamActionResultPayload(request.requestId(), false, exception.feedback()));
        }
    }

    public UserManager getUserManager() {
        return Objects.requireNonNull(userManager, "The quest user service is not running");
    }

    public boolean isRunning(MinecraftServer server) {
        return userManager != null && userManager.isRunning(server);
    }

    public void unloadPlayer(UUID playerId) {
        lastTravelPositions.remove(playerId);
        lastExperienceLevels.remove(playerId);
        if (userManager != null) {
            userManager.unloadPlayer(playerId);
        }
    }

    public Optional<User> getUser(UUID playerId) {
        return userManager == null ? Optional.empty() : userManager.getUser(playerId);
    }

    private void refreshUsers(Collection<UUID> playerIds) {
        if (server == null) {
            return;
        }
        Set<UUID> recipients = new HashSet<>(playerIds);
        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                restoreAttributeRewards(player);
                if (updatePlayerObjectives(player)) {
                    recipients.addAll(getUserManager().getProgressRecipients(playerId));
                }
            }
        }
        for (UUID recipient : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(recipient);
            if (player != null) {
                sendProgress(player);
            }
        }
    }

    protected void onPlayerLogin(ServerPlayer player) {
        getUserManager().loadPlayer(player);
        syncTeamState(player);
        restoreAttributeRewards(player);
        checkPlayerObjectives(player);
        getUserManager().getPlayerTeam(player.getUUID()).ifPresent(team -> {
            for (UUID id : team.getMembers().keySet()) {
                ServerPlayer member = player.level().getServer().getPlayerList().getPlayer(id);
                if (member != null && member != player) {
                    syncTeamState(member);
                }
            }
        });
    }

    public void restoreProgressRecipientAttributes(ServerPlayer player) {
        for (UUID id : getUserManager().getProgressRecipients(player.getUUID())) {
            ServerPlayer member = player.level().getServer().getPlayerList().getPlayer(id);
            if (member != null) {
                restoreAttributeRewards(member);
            }
        }
    }

    protected void restoreAttributeRewards(ServerPlayer player) {
        getUser(player.getUUID()).ifPresent(user -> QuestReloadDelegate.INSTANCE.getData().restoreAttributeRewards(user, player));
    }

    protected void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            getUser(player.getUUID()).ifPresent(user -> {
                ObjectiveContext context = ObjectiveContext.createContext(player.level())
                        .withParameter(ObjectiveContext.PLAYER, player)
                        .withParameter(ObjectiveContext.ENTITY, entity)
                        .build();
                if (QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, QuestObjectiveTypes.ENTITY, context)) {
                    syncProgress(player);
                }
            });
        }
    }

    public void onAdvancementEarned(ServerPlayer player, Identifier advancement) {
        getUser(player.getUUID()).ifPresent(user -> {
            ObjectiveContext context = ObjectiveContext.createContext(player.level())
                    .withParameter(ObjectiveContext.PLAYER, player)
                    .withParameter(ObjectiveContext.ADVANCEMENT, advancement)
                    .build();
            if (QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, QuestObjectiveTypes.ADVANCEMENT, context)) {
                syncProgress(player);
            }
        });
    }

    protected void syncProgress(ServerPlayer player) {
        for (UUID recipient : getUserManager().getProgressRecipients(player.getUUID())) {
            ServerPlayer member = player.level().getServer().getPlayerList().getPlayer(recipient);
            if (member != null) {
                sendProgress(member);
            }
        }
    }

    protected void sendProgress(ServerPlayer player) {
        if (getUserManager().isReady(player.getUUID())) {
            sendPayload(player, QuestProgressSyncPayload.create(player));
        }
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
            ObjectiveContext context = ObjectiveContext.createContext(player.level())
                    .withParameter(ObjectiveContext.PLAYER, player).withParameter(ObjectiveContext.STATISTIC, statistic).build();
            if (QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, QuestObjectiveTypes.STATISTIC, context)) {
                syncProgress(player);
            }
        });
    }

    public void checkPlayerObjectives(ServerPlayer player) {
        if (updatePlayerObjectives(player)) {
            syncProgress(player);
        }
    }

    private boolean updatePlayerObjectives(ServerPlayer player) {
        lastTravelPositions.put(player.getUUID(), GlobalPos.of(player.level().dimension(), player.blockPosition()));
        lastExperienceLevels.put(player.getUUID(), player.experienceLevel);
        return updatePlayerObjectives(player, QuestObjectiveTypes.COLLECT_ITEM, QuestObjectiveTypes.ADVANCEMENT,
                QuestObjectiveTypes.EXPERIENCE_LEVELS, QuestObjectiveTypes.STATISTIC, QuestObjectiveTypes.TRAVEL_DIMENSION,
                QuestObjectiveTypes.TRAVEL_BIOME, QuestObjectiveTypes.TRAVEL_COORDINATES, QuestObjectiveTypes.TRAVEL_STRUCTURE);
    }

    private void triggerPlayerObjectives(ServerPlayer player, QuestObjectiveSerializer<?>... types) {
        if (updatePlayerObjectives(player, types)) {
            syncProgress(player);
        }
    }

    private boolean updatePlayerObjectives(ServerPlayer player, QuestObjectiveSerializer<?>... types) {
        return getUser(player.getUUID()).map(user -> {
            ObjectiveContext context = ObjectiveContext.createContext(player.level()).withParameter(ObjectiveContext.PLAYER, player).build();
            boolean changed = false;
            for (QuestObjectiveSerializer<?> type : types) {
                changed |= QuestReloadDelegate.INSTANCE.getData().handleQuestTriggerChanges(user, type, context);
            }
            return changed;
        }).orElse(false);
    }

    public void stop() {
        try {
            if (userManager != null) {
                userManager.stop();
            }
        } finally {
            userManager = null;
            lastTravelPositions.clear();
            lastExperienceLevels.clear();
            server = null;
        }
    }
}
