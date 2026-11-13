package dev.veryue.vuequests;

import dev.veryue.vuequests.command.QuestDebugCommand;
import dev.veryue.vuequests.command.QuestExportCommand;
import dev.veryue.vuequests.command.QuestProgressCommands;
import dev.veryue.vuequests.command.QuestReloadCommand;
import dev.veryue.vuequests.command.QuestWebEditorCommand;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.editor.QuestEditorServer;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import dev.veryue.vuequests.network.QuestDebugTogglePayload;
import dev.veryue.vuequests.network.QuestProgressSyncPayload;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import dev.veryue.vuequests.network.QuestSyncPayload;
import dev.veryue.vuequests.network.QuestViewSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class VeryUEQuestsFabric extends VeryUEMod implements ModInitializer {

    @Override
    public void onInitialize() {
        VeryUERegistries.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            QuestProgressCommands.register(dispatcher, this::syncProgress);
            QuestDebugCommand.register(dispatcher, this::sendDebugToggle);
            QuestExportCommand.register(dispatcher);
            QuestReloadCommand.register(dispatcher, this::reloadServerConfig);
            QuestWebEditorCommand.register(dispatcher);
        });
        PayloadTypeRegistry.clientboundPlay().register(QuestSyncPayload.TYPE, QuestSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestViewSyncPayload.TYPE, QuestViewSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestProgressSyncPayload.TYPE, QuestProgressSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestDebugTogglePayload.TYPE, QuestDebugTogglePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestScreenOpenedPayload.TYPE, QuestScreenOpenedPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClaimQuestRewardPayload.TYPE, ClaimQuestRewardPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(InteractQuestObjectivePayload.TYPE, InteractQuestObjectivePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(InteractQuestObjectivePayload.TYPE, (payload, context) -> {
            if (payload.handle(context.player())) {
                syncProgress(context.player());
                checkPlayerObjectives(context.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(QuestScreenOpenedPayload.TYPE,
                (payload, context) -> {
                    payload.handle(context.player());
                    syncQuests(context.player());
                });
        ServerPlayNetworking.registerGlobalReceiver(ClaimQuestRewardPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (payload.handle(player)) {
                syncProgress(player);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            onPlayerLogin(handler.player);
            syncServerConfig(handler.player);
            syncQuests(handler.player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            restoreAttributeRewards(newPlayer);
            checkPlayerObjectives(newPlayer);
        });
        ServerPlayerEvents.LEAVE.register(player -> unloadPlayer(player.getUUID()));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> onPlayerMoved(player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerMoved(player);
                onExperienceChanged(player);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onEntityDeath);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    syncServerConfig(player);
                    restoreAttributeRewards(player);
                    syncQuests(player);
                }
            }
        });
        DataResourceLoader.get()
                .registerReloadListener(QuestReloadDelegate.ID, registries -> {
                    loadServerConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
                    return new QuestReloadListener(registries);
                });
        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        //ServerLifecycleEvents.SERVER_STARTED.register(QuestEditorServer::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stop();
            QuestEditorServer.stop();
            QuestReloadDelegate.INSTANCE.clear();
        });
    }

    @Override
    protected void syncServerConfig(ServerPlayer player) {
        ServerPlayNetworking.send(player, new QuestViewSyncPayload(getServerConfig().questView()));
    }

    @Override
    protected void syncQuests(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, QuestSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, QuestSyncPayload.create(player));
        }
    }

    @Override
    protected void syncProgress(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, QuestProgressSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, QuestProgressSyncPayload.create(player));
        }
    }

    private boolean sendDebugToggle(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, QuestDebugTogglePayload.TYPE)) {
            return false;
        }
        ServerPlayNetworking.send(player, QuestDebugTogglePayload.INSTANCE);
        return true;
    }
}
