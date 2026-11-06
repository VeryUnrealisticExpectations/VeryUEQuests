package dev.veryue.vuequests.models;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class QuestObjectiveProgress<T, TYPE> {

    public static final Codec<QuestObjectiveProgress<?, ?>> CODEC = Codec.lazyInitialized(() -> VeryUERegistries.QUEST_OBJECTIVE_TYPES
            .byNameCodec()
            .dispatch("type", QuestObjectiveProgress::getType, QuestObjectiveProgress::codec));

    private final UUID objectiveId;
    private final QuestObjectiveSerializer<TYPE> type;
    @Nullable
    private TYPE currentProgress;


    public QuestObjectiveProgress(UUID objectiveId, QuestObjectiveSerializer<TYPE> type) {
        this(objectiveId, type, null);
    }

    public QuestObjectiveProgress(UUID objectiveId, QuestObjectiveSerializer<TYPE> type, @Nullable TYPE currentProgress) {
        this.objectiveId = Objects.requireNonNull(objectiveId);
        this.type = Objects.requireNonNull(type);
        this.currentProgress = currentProgress;
    }

    private static <P> MapCodec<QuestObjectiveProgress<?, P>> codec(QuestObjectiveSerializer<P> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(QuestObjectiveProgress::getObjectiveId),
                type.progressCodec().optionalFieldOf("current_progress")
                        .forGetter(progress -> Optional.ofNullable(progress.getCurrentProgress()))
        ).apply(instance, (id, progress) -> new QuestObjectiveProgress<>(id, type, progress.orElse(null))));
    }

    public QuestObjectiveSerializer<TYPE> getType() {
        return type;
    }

    public UUID getObjectiveId() {
        return objectiveId;
    }

    public void increment(User user, QuestObjective<T, TYPE> objective, ObjectiveContext context) throws IllegalArgumentException {
        validateObjective(objective);
        objective.incrementObjectiveProgress(user, this, context);
    }

    public boolean isComplete(QuestObjective<T, TYPE> objective) throws IllegalArgumentException {
        validateObjective(objective);
        return objective.isComplete(this);
    }

    private void validateObjective(QuestObjective<T, TYPE> objective) {
        if (!objectiveId.equals(objective.getObjectiveId())) {
            throw new IllegalArgumentException("Progress belongs to a different objective.");
        }
        if (type != objective.getType()) {
            throw new IllegalArgumentException("Progress belongs to a different objective type.");
        }
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
