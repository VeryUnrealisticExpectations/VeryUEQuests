package dev.veryue.vuequests;

import dev.veryue.vuequests.command.QuestDebugCommand;
import dev.veryue.vuequests.command.QuestProgressCommands;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.editor.QuestEditorServer;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import dev.veryue.vuequests.network.QuestDebugTogglePayload;
import dev.veryue.vuequests.network.QuestProgressSyncPayload;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import dev.veryue.vuequests.network.QuestSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;

public class VeryUEQuestsFabric extends VeryUEMod implements ModInitializer {

    @Override
    public void onInitialize() {
        VeryUERegistries.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            QuestProgressCommands.register(dispatcher, this::syncProgress);
            QuestDebugCommand.register(dispatcher, this::sendDebugToggle);
        });
        PayloadTypeRegistry.playS2C().register(QuestSyncPayload.TYPE, QuestSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(QuestProgressSyncPayload.TYPE, QuestProgressSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(QuestDebugTogglePayload.TYPE, QuestDebugTogglePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(QuestScreenOpenedPayload.TYPE, QuestScreenOpenedPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimQuestRewardPayload.TYPE, ClaimQuestRewardPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(InteractQuestObjectivePayload.TYPE, InteractQuestObjectivePayload.STREAM_CODEC);
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
            syncQuests(handler.player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            restoreAttributeRewards(newPlayer);
            checkPlayerObjectives(newPlayer);
        });
        ServerPlayerEvents.LEAVE.register(player -> unloadPlayer(player.getUUID()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> onPlayerMoved(player));
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
                    restoreAttributeRewards(player);
                    syncQuests(player);
                }
            }
        });
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(QuestReloadDelegate.ID, QuestReloadListener::new);
        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        ServerLifecycleEvents.SERVER_STARTED.register(QuestEditorServer::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stop();
            QuestEditorServer.stop();
            QuestReloadDelegate.INSTANCE.clear();
        });
    }

    private void syncQuests(ServerPlayer player) {
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
