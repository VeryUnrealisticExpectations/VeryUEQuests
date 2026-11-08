package dev.veryue.vuequests.models.objective;

import dev.veryue.vuequests.models.QuestObjectiveProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class CountedQuestObjective<T> extends QuestObjective<T, Integer> {

    private final int count;

    protected CountedQuestObjective(UUID objectiveId, T value, int count) {
        super(objectiveId, value);
        if (count < 1) {
            throw new IllegalArgumentException("Objective count must be positive.");
        }
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    @Override
    protected MutableComponent formatProgress(@Nullable Integer progress, boolean complete) {
        int target = getCount();
        int displayedCount = complete ? target : Mth.clamp(getProgressCount(progress), 0, target);
        return Component.literal(displayedCount + "/" + target);
    }

    protected int getProgressCount(@Nullable Integer progress) {
        return progress == null ? 0 : progress;
    }

    @Override
    public boolean isComplete(QuestObjectiveProgress<T, Integer> progress) {
        return getProgressCount(progress.getCurrentProgress()) >= getCount();
    }
}
