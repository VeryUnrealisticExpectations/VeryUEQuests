package dev.veryue.vuequests.models.objective;

import dev.veryue.vuequests.models.QuestObjectiveProgress;

import java.util.UUID;

public abstract class BooleanQuestObjective<T> extends QuestObjective<T, Boolean> {

    protected BooleanQuestObjective(UUID objectiveId, T value) {
        super(objectiveId, value);
    }

    @Override
    public boolean isComplete(QuestObjectiveProgress<T, Boolean> progress) {
        return Boolean.TRUE.equals(progress.getCurrentProgress());
    }
}
