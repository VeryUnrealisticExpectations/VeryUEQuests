package dev.veryue.vuequests.data.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;

public abstract class QuestLoader {

    public abstract QuestData loadQuests(ResourceManager resourceManager, HolderLookup.Provider registryLookup);

}
