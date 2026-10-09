package dev.veryue.vuequests;

import dev.veryue.vuequests.editor.QuestEditorServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class VeryUEQuestsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> QuestEditorServer.start());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> QuestEditorServer.stop());
    }
}
