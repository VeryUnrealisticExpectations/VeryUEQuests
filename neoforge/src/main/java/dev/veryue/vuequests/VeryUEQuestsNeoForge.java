package dev.veryue.vuequests;

import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.command.QuestDebugCommand;
import dev.veryue.vuequests.command.QuestProgressCommands;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.editor.QuestEditorServer;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.QuestDebugTogglePayload;
import dev.veryue.vuequests.network.QuestProgressSyncPayload;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import dev.veryue.vuequests.network.QuestSyncPayload;
import dev.veryue.vuequests.platform.NeoForgePlatformHelper;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent.ProgressType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

@Mod(MOD_ID)
public class VeryUEQuestsNeoForge extends VeryUEMod {

    public VeryUEQuestsNeoForge(IEventBus eventBus) {
        ((NeoForgePlatformHelper) PlatformHelper.INSTANCE).init(eventBus);
        VeryUERegistries.init();
        eventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onAdvancementProgress);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        QuestProgressCommands.register(event.getDispatcher(), this::syncProgress);
        QuestDebugCommand.register(event.getDispatcher(), this::sendDebugToggle);
    }

    private void syncProgress(ServerPlayer player) {
        if (player.connection.hasChannel(QuestProgressSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, QuestProgressSyncPayload.create(player));
        }
    }

    private boolean sendDebugToggle(ServerPlayer player) {
        if (!player.connection.hasChannel(QuestDebugTogglePayload.TYPE)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, QuestDebugTogglePayload.INSTANCE);
        return true;
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(QuestSyncPayload.TYPE, QuestSyncPayload.STREAM_CODEC,
                        (payload, context) -> ClientQuestData.update(payload.sections(), payload.user()))
                .optional()
                .playToClient(QuestProgressSyncPayload.TYPE, QuestProgressSyncPayload.STREAM_CODEC,
                        (payload, context) -> ClientQuestData.updateProgress(payload.user()))
                .playToClient(QuestDebugTogglePayload.TYPE, QuestDebugTogglePayload.STREAM_CODEC,
                        (payload, context) -> ClientQuestData.toggleDebug())
                .playToServer(QuestScreenOpenedPayload.TYPE, QuestScreenOpenedPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        payload.handle(player);
                        PacketDistributor.sendToPlayer(player, QuestSyncPayload.create(player));
                    }
                })
                .playToServer(ClaimQuestRewardPayload.TYPE, ClaimQuestRewardPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && payload.handle(player)) {
                        syncProgress(player);
                    }
                });
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), QuestSyncPayload.create(event.getPlayer()));
        } else {
            for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                restoreAttributeRewards(player);
                PacketDistributor.sendToPlayer(player, QuestSyncPayload.create(player));
            }
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        QuestEditorServer.start(event.getServer());
    }

    private void onServerStarting(ServerStartingEvent event) {
        start(event.getServer());
    }

    private void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onPlayerLogin(player);
        }
    }

    private void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreAttributeRewards(player);
        }
    }

    private void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            unloadPlayer(player.getUUID());
        }
    }

    private void onLivingDeath(LivingDeathEvent event) {
        onEntityDeath(event.getEntity(), event.getSource());
    }

    private void onAdvancementProgress(AdvancementProgressEvent event) {
        if (event.getProgressType() == ProgressType.GRANT && event.getAdvancementProgress().isDone()
                && event.getEntity() instanceof ServerPlayer player) {
            onAdvancementEarned(player, event.getAdvancement().id());
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestReloadListener());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        stop();
        QuestEditorServer.stop();
        QuestReloadDelegate.INSTANCE.clear();
    }
}
