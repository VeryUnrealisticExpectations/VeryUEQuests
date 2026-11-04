package dev.veryue.vuequests.models;

import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public abstract class User {

    private final UUID uuid;
    private final Set<UUID> completedQuests;
    private final Set<UUID> completedObjectives;
    private final Set<UUID> claimedRewards;

    protected User(UUID uuid) {
        this(uuid, Set.of(), Set.of(), Set.of());
    }

    protected User(UUID uuid, Set<UUID> completedQuests, Set<UUID> completedObjectives, Set<UUID> claimedRewards) {
        this.uuid = Objects.requireNonNull(uuid);
        this.completedQuests = new LinkedHashSet<>(completedQuests);
        this.completedObjectives = new LinkedHashSet<>(completedObjectives);
        this.claimedRewards = new LinkedHashSet<>(claimedRewards);
    }

    public UUID getUuid() {
        return uuid;
    }

    public Set<UUID> getCompletedQuests() {
        return Set.copyOf(completedQuests);
    }

    public Set<UUID> getCompletedObjectives() {
        return Set.copyOf(completedObjectives);
    }

    public Set<UUID> getClaimedRewards() {
        return Set.copyOf(claimedRewards);
    }

    public abstract void grantQuestCompletion(Quest quest);

    public abstract void grantQuestCompletion(UUID uuid);

    public abstract void handleReward(Quest quest);

    public abstract boolean hasCompletedQuest(Quest quest);

    public abstract boolean hasCompletedObjective(QuestObjective<?> objective);

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
