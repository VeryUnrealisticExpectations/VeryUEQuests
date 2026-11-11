package dev.veryue.vuequests.models;

import com.mojang.datafixers.Products.P5;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Set<UUID> claimedRewards = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRewardClaims = ConcurrentHashMap.newKeySet();


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
        this.claimedRewards.addAll(claimedRewards);
        this.questObjectiveProgress = new HashMap<>(questObjectiveProgress);
    }


    public Map<UUID, QuestObjectiveProgress<?, ?>> getQuestObjectiveProgress() {
        return questObjectiveProgress;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T, TYPE> QuestObjectiveProgress<T, TYPE> getObjectiveProgress(QuestObjective<T, TYPE> objective) {
        UUID objectiveId = objective.getObjectiveId();
        QuestObjectiveProgress<?, ?> progress = questObjectiveProgress.get(objectiveId);
        if (progress == null || !objectiveId.equals(progress.getObjectiveId()) || progress.getType() != objective.getType()) {
            return null;
        }
        return (QuestObjectiveProgress<T, TYPE>) progress;
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

    public boolean tryClaimReward(RewardType<?> reward, ServerPlayer player) {
        UUID rewardId = reward.getRewardId();
        if (!pendingRewardClaims.add(rewardId)) {
            return false;
        }
        try {
            return !claimedRewards.contains(rewardId) && reward.tryGiveReward(player) && claimedRewards.add(rewardId);
        } finally {
            pendingRewardClaims.remove(rewardId);
        }
    }

    public void completeObjective(@NotNull QuestObjective<?, ?> objective) {
        completedObjectives.add((objective.getObjectiveId()));
    }

    public synchronized void resetQuestProgress(Quest quest) {
        completedQuests.remove(quest.questId());
        for (QuestObjective<?, ?> objective : quest.objectives()) {
            clearObjectiveProgress(objective.getObjectiveId());
        }
        for (RewardType<?> reward : quest.rewards()) {
            claimedRewards.remove(reward.getRewardId());
        }
    }

    public synchronized void completeQuestProgress(Quest quest) {
        quest.objectives().forEach(this::completeObjective);
        grantQuestCompletion(quest);
    }

    public synchronized void resetObjectiveProgress(Quest quest, UUID objectiveId) {
        clearObjectiveProgress(objectiveId);
        completedQuests.remove(quest.questId());
    }

    public synchronized void completeObjectiveProgress(Quest quest, QuestObjective<?, ?> objective) {
        completeObjective(objective);
        if (quest.objectives().stream().allMatch(this::hasCompletedObjective)) {
            grantQuestCompletion(quest);
        }
    }

    private void clearObjectiveProgress(UUID objectiveId) {
        completedObjectives.remove(objectiveId);
        questObjectiveProgress.remove(objectiveId);
    }

    public abstract void grantQuestCompletion(Quest quest);

    public abstract void grantQuestCompletion(UUID uuid);

    public abstract void handleReward(Quest quest); // todo; not sure if i'll use this one

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
