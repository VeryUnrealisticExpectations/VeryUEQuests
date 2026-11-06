package dev.veryue.vuequests.client;

import dev.veryue.vuequests.client.editor.ClientEditorResources;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import dev.veryue.vuequests.network.QuestSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class VeryUEQuestsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientEditorResources.initialize();
        ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.TYPE,
                (payload, context) -> ClientQuestData.update(payload.sections(), payload.user()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientQuestData.clear());
        KeyBindingHelper.registerKeyBinding(VeryUEKeyMappings.OPEN_QUEST_MENU);
        ClientTickEvents.END_CLIENT_TICK.register(client -> VeryUEKeyMappings.onClientTick(client, this::notifyQuestScreenOpened));
        ClientTickEvents.END_CLIENT_TICK.register(ClientEditorResources::tick);
    }

    private void notifyQuestScreenOpened() {
        if (ClientPlayNetworking.canSend(QuestScreenOpenedPayload.TYPE)) {
            ClientPlayNetworking.send(QuestScreenOpenedPayload.INSTANCE);
        }
    }
}
