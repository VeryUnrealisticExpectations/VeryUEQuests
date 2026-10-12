package dev.veryue.vuequests;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.editor.QuestEditorServer;
import dev.veryue.vuequests.platform.NeoForgePlatformHelper;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

@Mod(MOD_ID)
public class VeryUEQuestsNeoForge {

    public VeryUEQuestsNeoForge(IEventBus eventBus) {
        ((NeoForgePlatformHelper) PlatformHelper.INSTANCE).init(eventBus);
        VeryUERegistries.init();
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarted(ServerStartedEvent event) {
        QuestEditorServer.start();
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestReloadListener());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        QuestEditorServer.stop();
        QuestReloadDelegate.INSTANCE.clear();
    }
}
