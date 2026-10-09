package dev.veryue.vuequests;

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
        return null;
    }

    @Override
    public String getName() {
        return super.getName();
    }
}
