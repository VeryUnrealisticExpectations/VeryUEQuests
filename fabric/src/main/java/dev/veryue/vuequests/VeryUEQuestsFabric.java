package dev.veryue.vuequests;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.editor.QuestEditorServer;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import dev.veryue.vuequests.network.QuestSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;

import java.util.List;

public class VeryUEQuestsFabric extends VeryUEMod implements ModInitializer {

    @Override
    public void onInitialize() {
        VeryUERegistries.init();
        PayloadTypeRegistry.playS2C().register(QuestSyncPayload.TYPE, QuestSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(QuestScreenOpenedPayload.TYPE, QuestScreenOpenedPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(QuestScreenOpenedPayload.TYPE,
                (payload, context) -> payload.handle(context.player()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            loadPlayer(handler.player.getUUID());
            syncQuests(handler.player);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                server.getPlayerList().getPlayers().forEach(this::syncQuests);
            }
        });
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(QuestReloadDelegate.ID, QuestReloadListener::new);
        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> QuestEditorServer.start(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            QuestEditorServer.stop();
            QuestReloadDelegate.INSTANCE.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> this.stop());
    }

    private void syncQuests(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, QuestSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new QuestSyncPayload(List.copyOf(QuestReloadDelegate.INSTANCE.getData().sections().values())));
        }
    }
}
