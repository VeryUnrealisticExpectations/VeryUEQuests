package dev.veryue.vuequests;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class QuestReloadListener extends ContextAwareReloadListener {


    @Override
    public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier,
                                          ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler,
                                          ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor,
                                          Executor gameExecutor) {
        return QuestReloadDelegate.INSTANCE.reload(preparationBarrier, resourceManager, getRegistryLookup(),
                preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
    }

    @Override
    public String getName() {
        return QuestReloadDelegate.ID.toString();
    }
}
