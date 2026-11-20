package dev.veryue.vuequests.client;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class ClientQuestReloadListener implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_languages");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        ClientQuestData.refreshTranslations();
    }
}
