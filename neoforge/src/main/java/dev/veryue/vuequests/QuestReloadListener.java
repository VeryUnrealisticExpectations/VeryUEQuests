package dev.veryue.vuequests;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class QuestReloadListener extends ContextAwareReloadListener {

    @Override
    public CompletableFuture<Void> reload(SharedState state, Executor backgroundExecutor,
                                          PreparationBarrier preparationBarrier, Executor gameExecutor) {
        return QuestReloadDelegate.INSTANCE.reload(preparationBarrier, state.resourceManager(), getRegistryLookup(),
                backgroundExecutor, gameExecutor);
    }

    @Override
    public String getName() {
        return QuestReloadDelegate.ID.toString();
    }
}
