package dev.veryue.vuequests.models;

import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.objective.QuestObjective;

import java.util.Set;
import java.util.UUID;

public abstract class User {

    // how do we know a user has completed a quest? well, we could have a list of quest completions..

    // todo; have to figure out how to handle repeatable quests, but maybe not for release.
    private Set<UUID> completedQuests;
    private Set<UUID> completedObjectives; // todo; not sure
    private Set<UUID> claimedRewards;






    public abstract void grantQuestCompletion(Quest quest);

    public abstract void grantQuestCompletion(UUID uuid);

    public abstract void handleReward(Quest quest);

    public abstract boolean hasCompletedQuest(Quest quest);
    //public abstract boolean hasCompletedQuest(UUID questId);

    public abstract boolean hasCompletedObjective(QuestObjective<?> objective);
    //public abstract



}
