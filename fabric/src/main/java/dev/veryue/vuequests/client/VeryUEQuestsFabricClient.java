package dev.veryue.vuequests.client;

import dev.veryue.vuequests.network.QuestSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class VeryUEQuestsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.TYPE,
                (payload, context) -> ClientQuestData.update(payload.sections()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientQuestData.clear());
        KeyBindingHelper.registerKeyBinding(VeryUEKeyMappings.OPEN_QUEST_MENU);
        ClientTickEvents.END_CLIENT_TICK.register(VeryUEKeyMappings::onClientTick);
    }
}
