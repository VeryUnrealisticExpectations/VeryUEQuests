package dev.veryue.vuequests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.editor.ClientEditorResources;
import dev.veryue.vuequests.client.lang.ClientQuestLanguages;
import dev.veryue.vuequests.client.screens.LinearQuestCustomization;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

@Mod(value = MOD_ID, dist = Dist.CLIENT)
public class VeryUEQuestsNeoForgeClient {

    private final QuestCanvasKeyMappings canvasKeys = new QuestCanvasKeyMappings(
            (name, keyCode) -> new KeyMapping(name, QuestCanvasKeyConflictContext.INSTANCE, KeyModifier.SHIFT,
                    InputConstants.Type.KEYSYM, keyCode, QuestCanvasKeyMappings.CATEGORY),
            KeyMapping::isActiveAndMatches);
    private final QuestMenuKeyMappings menuKeys = new QuestMenuKeyMappings(
            (name, keyCode) -> new KeyMapping(name, QuestMenuKeyConflictContext.INSTANCE,
                    InputConstants.Type.KEYSYM, keyCode, QuestMenuKeyMappings.CATEGORY),
            KeyMapping::isActiveAndMatches);
    private final QuestMenuActions questActions = new QuestMenuActions(
            () -> sendToServer(QuestScreenOpenedPayload.INSTANCE),
            (questId, objectiveId) -> sendToServer(new InteractQuestObjectivePayload(questId, objectiveId)),
            (questId, rewardId) -> sendToServer(new ClaimQuestRewardPayload(questId, rewardId)));

    public VeryUEQuestsNeoForgeClient(IEventBus eventBus) {
        ClientQuestLanguages.initialize(FMLPaths.CONFIGDIR.get());
        ClientEditorResources.initialize();
        eventBus.addListener(this::registerReloadListeners);
        eventBus.addListener(this::registerKeyMappings);
        eventBus.addListener(this::onConstructMod);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void onConstructMod(FMLConstructModEvent event) {
        // FancyMenu reads saved layouts before NeoForge's client setup event.
        if (ModList.get().isLoaded(LinearQuestCustomization.FANCY_MENU_MOD_ID)) {
            event.enqueueWork(LinearQuestCustomization::initialize);
        }
    }

    private void registerReloadListeners(AddClientReloadListenersEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "quest_languages");
        ResourceManagerReloadListener listener = ClientQuestData::refreshTranslations;
        event.addListener(id, listener);
        event.addDependency(VanillaClientListeners.LANGUAGE, id);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        VeryUEKeyMappings.OPEN_QUEST_MENU.setKeyConflictContext(KeyConflictContext.IN_GAME);
        event.register(VeryUEKeyMappings.OPEN_QUEST_MENU);
        canvasKeys.register(mapping -> {
            mapping.setKeyConflictContext(QuestCanvasKeyConflictContext.INSTANCE);
            event.register(mapping);
        });
        menuKeys.register(event::register);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        ClientEditorResources.tick(Minecraft.getInstance());
        VeryUEKeyMappings.onClientTick(Minecraft.getInstance(), questActions, this::sendToServer, canvasKeys, menuKeys);
    }

    private void sendToServer(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(payload.type())) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestData.clear();
    }
}
