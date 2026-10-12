package dev.veryue.vuequests;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class QuestReloadListener implements IdentifiableResourceReloadListener {

    private final HolderLookup.Provider registryLookup;

    public QuestReloadListener(HolderLookup.Provider registryLookup) {
        this.registryLookup = registryLookup;
    }

    @Override
    public ResourceLocation getFabricId() {
        return QuestReloadDelegate.ID;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return QuestReloadDelegate.INSTANCE.reload(preparationBarrier, resourceManager, registryLookup,
                preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
    }

    @Override
    public String getName() {
        return QuestReloadDelegate.ID.toString();
    }
}
