package dev.veryue.vuequests.data.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;

import java.nio.file.Path;

public class ConfigQuestLoader extends QuestLoader {

    private final QuestConfigStorage storage;

    public ConfigQuestLoader(Path directory) {
        storage = new QuestConfigStorage(directory);
    }

    @Override
    public QuestData loadQuests(ResourceManager resourceManager, HolderLookup.Provider registryLookup) {
        return decodeSections(storage.read(), registryLookup);
    }
}
