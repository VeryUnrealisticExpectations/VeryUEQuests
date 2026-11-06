package dev.veryue.vuequests.models;

import dev.veryue.vuequests.models.objective.QuestObjective;
import org.jetbrains.annotations.Nullable;

public class QuestObjectiveProgress<T, TYPE> {

    private QuestObjective<T, TYPE> objective; // todo; might be a UUID instead.
    @Nullable
    private TYPE currentProgress;


    public QuestObjectiveProgress(QuestObjective<T, TYPE> objective) {
        this.objective = objective;
        currentProgress = null;
    }

    public void increment(User user) {
        objective.incrementObjectiveProgress(user, this);
    }


    public void setCurrentProgress(TYPE progress) {
        this.currentProgress = progress;
    }


    public TYPE getCurrentProgress() {
        return currentProgress;
    }
}
