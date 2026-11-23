package dev.veryue.vuequests.client;

import dev.veryue.vuequests.client.editor.ClientEditorResources;
import dev.veryue.vuequests.client.lang.ClientQuestLanguages;
import dev.veryue.vuequests.client.screens.LinearQuestCustomization;
import dev.veryue.vuequests.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.PackType;

public class VeryUEQuestsFabricClient implements ClientModInitializer {

    private final QuestCanvasKeyMappings canvasKeys = new QuestCanvasKeyMappings();
    private final QuestMenuKeyMappings menuKeys = new QuestMenuKeyMappings();
    private final QuestMenuActions questActions = new QuestMenuActions(
            () -> sendToServer(QuestScreenOpenedPayload.INSTANCE),
            (questId, objectiveId) -> sendToServer(new InteractQuestObjectivePayload(questId, objectiveId)),
            (questId, rewardId) -> sendToServer(new ClaimQuestRewardPayload(questId, rewardId)));

    @Override
    public void onInitializeClient() {
        ClientQuestLanguages.initialize(FabricLoader.getInstance().getConfigDir());
        ClientEditorResources.initialize();
        ResourceLoader resources = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        resources.registerReloadListener(ClientQuestReloadListener.ID, new ClientQuestReloadListener());
        resources.addListenerOrdering(ResourceReloaderKeys.Client.LANGUAGES, ClientQuestReloadListener.ID);
        ClientPlayNetworking.registerGlobalReceiver(TeamSyncPayload.TYPE, (payload, context) -> ClientTeamData.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(TeamActionResultPayload.TYPE, (payload, context) -> ClientTeamData.complete(payload));
        ClientPlayNetworking.registerGlobalReceiver(PersonalDataStatusPayload.TYPE,
                (payload, context) -> ClientQuestData.setDataStatus(payload.ready(), payload.message()));
        ClientPlayNetworking.registerGlobalReceiver(QuestViewSyncPayload.TYPE,
                (payload, context) -> ClientQuestData.setQuestView(payload.view()));
        ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.TYPE,
                (payload, context) -> ClientQuestData.update(payload.sections(), payload.user()));
        ClientPlayNetworking.registerGlobalReceiver(QuestProgressSyncPayload.TYPE,
                (payload, context) -> ClientQuestData.updateProgress(payload.user()));
        ClientPlayNetworking.registerGlobalReceiver(QuestDebugTogglePayload.TYPE, (payload, context) -> ClientQuestData.toggleDebug());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientQuestData.clear());
        KeyMappingHelper.registerKeyMapping(VeryUEKeyMappings.OPEN_QUEST_MENU);
        canvasKeys.register(KeyMappingHelper::registerKeyMapping);
        menuKeys.register(KeyMappingHelper::registerKeyMapping);
        if (FabricLoader.getInstance().isModLoaded(LinearQuestCustomization.FANCY_MENU_MOD_ID)) {
            LinearQuestCustomization.initialize();
        }
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                VeryUEKeyMappings.onClientTick(client, questActions, this::sendToServer, canvasKeys, menuKeys));
        ClientTickEvents.END_CLIENT_TICK.register(ClientEditorResources::tick);
    }

    private void sendToServer(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
