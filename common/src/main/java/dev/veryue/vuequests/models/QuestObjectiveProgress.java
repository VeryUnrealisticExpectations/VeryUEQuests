package dev.veryue.vuequests.models;

import dev.veryue.vuequests.models.objective.QuestObjective;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class QuestObjectiveProgress<T, TYPE> {

    private final UUID objectiveId;
    @Nullable
    private TYPE currentProgress;


    public QuestObjectiveProgress(UUID objectiveId) {
        this.objectiveId = Objects.requireNonNull(objectiveId);
        currentProgress = null;
    }

    public UUID getObjectiveId() {
        return objectiveId;
    }

    public void increment(User user, QuestObjective<T, TYPE> objective, ObjectiveContext context) {
        if (!objectiveId.equals(objective.getObjectiveId())) {
            throw new IllegalArgumentException("Progress belongs to a different objective.");
        }
        objective.incrementObjectiveProgress(user, this, context);
    }



    public void setCurrentProgress(@Nullable TYPE progress) {
        this.currentProgress = progress;
    }

    /**
     * @return The current progress, if null then there has been no progress made.
     */
    @Nullable
    public TYPE getCurrentProgress() {
        return currentProgress;
    }
}
