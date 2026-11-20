package dev.veryue.vuequests.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class ClientQuestReloadListener implements ResourceManagerReloadListener {
    static final Identifier ID = Identifier.fromNamespaceAndPath(MOD_ID, "quest_languages");

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        ClientQuestData.refreshTranslations(resources);
    }
}
