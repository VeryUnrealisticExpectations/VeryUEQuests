package dev.veryue.vuequests;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.editor.QuestEditorServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

public class VeryUEQuestsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        VeryUERegistries.init();
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(QuestReloadDelegate.ID, QuestReloadListener::new);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> QuestEditorServer.start());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            QuestEditorServer.stop();
            QuestReloadDelegate.INSTANCE.clear();
        });
    }
}
