package dev.veryue.vuequests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.editor.ClientEditorResources;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

@Mod(value = MOD_ID, dist = Dist.CLIENT)
public class VeryUEQuestsNeoForgeClient {

    private final QuestCanvasKeyMappings canvasKeys = new QuestCanvasKeyMappings(
            (name, keyCode) -> new KeyMapping(name, QuestCanvasKeyConflictContext.INSTANCE, KeyModifier.SHIFT,
                    InputConstants.Type.KEYSYM, keyCode, QuestCanvasKeyMappings.CATEGORY),
            KeyMapping::isActiveAndMatches);

    public VeryUEQuestsNeoForgeClient(IEventBus eventBus) {
        ClientEditorResources.initialize();
        eventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        VeryUEKeyMappings.OPEN_QUEST_MENU.setKeyConflictContext(KeyConflictContext.IN_GAME);
        event.register(VeryUEKeyMappings.OPEN_QUEST_MENU);
        canvasKeys.register(mapping -> {
            mapping.setKeyConflictContext(QuestCanvasKeyConflictContext.INSTANCE);
            event.register(mapping);
        });
    }

    private void onClientTick(ClientTickEvent.Post event) {
        ClientEditorResources.tick(Minecraft.getInstance());
        VeryUEKeyMappings.onClientTick(Minecraft.getInstance(), this::sendToServer, canvasKeys);
    }

    private void sendToServer(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(payload.type())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestData.clear();
    }
}
