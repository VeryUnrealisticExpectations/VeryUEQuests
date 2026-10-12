package dev.veryue.vuequests.data.quest;

import dev.veryue.vuequests.VeryUEConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class QuestReloadDelegate {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "quests");
    public static final QuestReloadDelegate INSTANCE = new QuestReloadDelegate(new FlatFileQuestLoader());

    private static final Logger LOGGER = LoggerFactory.getLogger(VeryUEConstants.MOD_ID);

    private final QuestLoader loader;
    private volatile QuestData data = QuestData.EMPTY;

    public QuestReloadDelegate(QuestLoader loader) {
        this.loader = loader;
    }

    public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                          HolderLookup.Provider registryLookup, ProfilerFiller preparationsProfiler,
                                          ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> {
            preparationsProfiler.push(ID.toString());
            try {
                return loader.loadQuests(resourceManager, registryLookup);
            } finally {
                preparationsProfiler.pop();
            }
        }, backgroundExecutor).thenCompose(preparationBarrier::wait).thenAcceptAsync(loaded -> {
            reloadProfiler.push(ID.toString());
            try {
                data = loaded;
                LOGGER.info("Loaded {} quest sections and {} quests", loaded.sections().size(), loaded.quests().size());
            } finally {
                reloadProfiler.pop();
            }
        }, gameExecutor);
    }

    public QuestData getData() {
        return data;
    }

    public void clear() {
        data = QuestData.EMPTY;
    }
}
