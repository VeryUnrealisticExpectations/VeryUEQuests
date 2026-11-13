package dev.veryue.vuequests;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class QuestReloadListener implements PreparableReloadListener {

    private final HolderLookup.Provider registryLookup;

    public QuestReloadListener(HolderLookup.Provider registryLookup) {
        this.registryLookup = registryLookup;
    }

    @Override
    public CompletableFuture<Void> reload(SharedState state, Executor backgroundExecutor,
                                          PreparationBarrier preparationBarrier, Executor gameExecutor) {
        return QuestReloadDelegate.INSTANCE.reload(preparationBarrier, state.resourceManager(), registryLookup,
                backgroundExecutor, gameExecutor);
    }

    @Override
    public String getName() {
        return QuestReloadDelegate.ID.toString();
    }
}
