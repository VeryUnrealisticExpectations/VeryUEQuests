package dev.veryue.vuequests.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

@Mod(value = MOD_ID, dist = Dist.CLIENT)
public class VeryUEQuestsNeoForgeClient {

    public VeryUEQuestsNeoForgeClient(IEventBus eventBus) {
        eventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        VeryUEKeyMappings.OPEN_QUEST_MENU.setKeyConflictContext(KeyConflictContext.IN_GAME);
        event.register(VeryUEKeyMappings.OPEN_QUEST_MENU);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        VeryUEKeyMappings.onClientTick(Minecraft.getInstance());
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestData.clear();
    }
}
