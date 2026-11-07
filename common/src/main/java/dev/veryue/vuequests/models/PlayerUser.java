package dev.veryue.vuequests.models;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerUser extends User {

    public static final Codec<PlayerUser> CODEC = RecordCodecBuilder.create(instance ->
            User.codec(instance).apply(instance, PlayerUser::new));

    public PlayerUser(UUID uuid) {
        super(uuid);
    }

    public PlayerUser(UUID uuid, Set<UUID> completedQuests, Set<UUID> completedObjectives, Set<UUID> claimedRewards) {
        super(uuid, completedQuests, completedObjectives, claimedRewards);
    }

    public PlayerUser(UUID uuid, Set<UUID> completedQuests, Set<UUID> completedObjectives, Set<UUID> claimedRewards,
                      Map<UUID, QuestObjectiveProgress<?, ?>> questObjectiveProgress) {
        super(uuid, completedQuests, completedObjectives, claimedRewards, questObjectiveProgress);
    }

    @Override
    public void grantQuestCompletion(Quest quest) {
        grantQuestCompletion(quest.questId());
    }

    @Override
    public void grantQuestCompletion(UUID uuid) {
        recordQuestCompletion(uuid);
    }

    @Override
    public void handleReward(Quest quest) {


    }

    @Override
    public boolean hasCompletedQuest(Quest quest) {
        return isQuestCompleted(quest.questId());
    }

    @Override
    public boolean hasCompletedObjective(QuestObjective<?, ?> objective) {
        return isObjectiveCompleted(objective.getObjectiveId());
    }
}
