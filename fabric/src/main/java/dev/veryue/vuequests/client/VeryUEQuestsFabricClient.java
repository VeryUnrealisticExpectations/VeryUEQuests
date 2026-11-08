package dev.veryue.vuequests.client;

import dev.veryue.vuequests.client.editor.ClientEditorResources;
import dev.veryue.vuequests.network.QuestProgressSyncPayload;
import dev.veryue.vuequests.network.QuestSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class VeryUEQuestsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientEditorResources.initialize();
        ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.TYPE,
                (payload, context) -> ClientQuestData.update(payload.sections(), payload.user()));
        ClientPlayNetworking.registerGlobalReceiver(QuestProgressSyncPayload.TYPE,
                (payload, context) -> ClientQuestData.updateProgress(payload.user()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientQuestData.clear());
        KeyBindingHelper.registerKeyBinding(VeryUEKeyMappings.OPEN_QUEST_MENU);
        ClientTickEvents.END_CLIENT_TICK.register(client -> VeryUEKeyMappings.onClientTick(client, this::sendToServer));
        ClientTickEvents.END_CLIENT_TICK.register(ClientEditorResources::tick);
    }

    private void sendToServer(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
