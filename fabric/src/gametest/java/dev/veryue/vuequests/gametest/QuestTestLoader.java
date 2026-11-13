package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;

public class QuestTestLoader extends QuestLoader {
    private final QuestData data;

    QuestTestLoader(QuestData data) {
        this.data = data;
    }

    @Override
    public QuestData loadQuests(ResourceManager resourceManager, HolderLookup.Provider registryLookup) {
        return data;
    }
}
