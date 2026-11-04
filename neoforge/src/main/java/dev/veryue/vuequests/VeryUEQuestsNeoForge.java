package dev.veryue.vuequests;

import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.editor.QuestEditorServer;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import dev.veryue.vuequests.network.QuestSyncPayload;
import dev.veryue.vuequests.platform.NeoForgePlatformHelper;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

@Mod(MOD_ID)
public class VeryUEQuestsNeoForge extends VeryUEMod {

    public VeryUEQuestsNeoForge(IEventBus eventBus) {
        ((NeoForgePlatformHelper) PlatformHelper.INSTANCE).init(eventBus);
        VeryUERegistries.init();
        eventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(QuestSyncPayload.TYPE, QuestSyncPayload.STREAM_CODEC,
                        (payload, context) -> ClientQuestData.update(payload.sections()))
                .optional()
                .playToServer(QuestScreenOpenedPayload.TYPE, QuestScreenOpenedPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        payload.handle(player);
                    }
                });
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        QuestSyncPayload payload = new QuestSyncPayload(List.copyOf(QuestReloadDelegate.INSTANCE.getData().sections().values()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), payload);
        } else {
            event.getPlayerList().getPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
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
            loadPlayer(player.getUUID());
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestReloadListener());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        QuestEditorServer.stop();
        QuestReloadDelegate.INSTANCE.clear();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        stop();
    }
}
