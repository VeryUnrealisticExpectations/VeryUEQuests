package dev.veryue.vuequests.models;

import com.mojang.datafixers.Products.P5;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class User {

    private static final Codec<Set<UUID>> UUID_SET_CODEC = UUIDUtil.STRING_CODEC.listOf()
            .xmap(Set::copyOf, ids -> ids.stream().sorted().toList());

    public static final Codec<Map<UUID, QuestObjectiveProgress<?, ?>>> QUEST_OBJECTIVE_PROGRESS_CODEC = Codec
            .unboundedMap(UUIDUtil.STRING_CODEC, QuestObjectiveProgress.CODEC)
            .validate(progressById -> {
                /*for (var entry : progressById.entrySet()) {
                    if (!entry.getKey().equals(entry.getValue().getObjectiveId())) {
                        return DataResult.error(() -> "Progress map key does not match objective UUID " + entry.getKey());
                    }
                }*/
                return DataResult.success(progressById);
            });

    private final UUID uuid;
    private final Set<UUID> completedQuests;
    private final Set<UUID> completedObjectives;
    private final Set<UUID> claimedRewards;


    private final Map<UUID, QuestObjectiveProgress<?, ?>> questObjectiveProgress;

    protected static <T extends User> P5<Mu<T>, UUID, Set<UUID>, Set<UUID>, Set<UUID>, Map<UUID, QuestObjectiveProgress<?, ?>>> codec(
            Instance<T> instance) {
        return instance.group(
                UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(User::getUuid),
                UUID_SET_CODEC.fieldOf("completed_quests").forGetter(User::getCompletedQuests),
                UUID_SET_CODEC.fieldOf("completed_objectives").forGetter(User::getCompletedObjectives),
                UUID_SET_CODEC.fieldOf("claimed_rewards").forGetter(User::getClaimedRewards),
                QUEST_OBJECTIVE_PROGRESS_CODEC.optionalFieldOf("quest_objective_progress", Map.of()).forGetter(User::getQuestObjectiveProgress)
        );
    }

    protected User(UUID uuid) {
        this(uuid, Set.of(), Set.of(), Set.of());
    }

    protected User(UUID uuid, Set<UUID> completedQuests, Set<UUID> completedObjectives, Set<UUID> claimedRewards) {
        this(uuid, completedQuests, completedObjectives, claimedRewards, Map.of());
    }

    protected User(UUID uuid, Set<UUID> completedQuests, Set<UUID> completedObjectives, Set<UUID> claimedRewards,
                   Map<UUID, QuestObjectiveProgress<?, ?>> questObjectiveProgress) {
        this.uuid = Objects.requireNonNull(uuid);
        this.completedQuests = new LinkedHashSet<>(completedQuests);
        this.completedObjectives = new LinkedHashSet<>(completedObjectives);
        this.claimedRewards = new LinkedHashSet<>(claimedRewards);
        this.questObjectiveProgress = new HashMap<>(questObjectiveProgress);
    }


    public Map<UUID, QuestObjectiveProgress<?, ?>> getQuestObjectiveProgress() {
        return questObjectiveProgress;
    }

    public <T, TYPE> QuestObjectiveProgress<T, TYPE> incrementObjectiveProgress(QuestObjective<T, TYPE> objective, ObjectiveContext context) {
        @SuppressWarnings("unchecked")
        QuestObjectiveProgress<T, TYPE> progress = (QuestObjectiveProgress<T, TYPE>)
                questObjectiveProgress.computeIfAbsent(objective.getObjectiveId(), id -> new QuestObjectiveProgress<>(id, objective.getType()));
        progress.increment(this, objective, context);
        return progress;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Set<UUID> getCompletedQuests() {
        return completedQuests;
    }

    public Set<UUID> getCompletedObjectives() {
        return completedObjectives;
    }

    public Set<UUID> getClaimedRewards() {
        return claimedRewards;
    }

    public void completeObjective(@NotNull QuestObjective<?, ?> objective) {
        completedObjectives.add((objective.getObjectiveId()));
    }

    public abstract void grantQuestCompletion(Quest quest);

    public abstract void grantQuestCompletion(UUID uuid);

    public abstract void handleReward(Quest quest);

    public abstract boolean hasCompletedQuest(Quest quest);

    public abstract boolean hasCompletedObjective(QuestObjective<?, ?> objective);

    protected final void recordQuestCompletion(UUID questId) {
        completedQuests.add(Objects.requireNonNull(questId));
    }

    protected final boolean isQuestCompleted(UUID questId) {
        return completedQuests.contains(questId);
    }

    protected final boolean isObjectiveCompleted(UUID objectiveId) {
        return completedObjectives.contains(objectiveId);
    }
}
