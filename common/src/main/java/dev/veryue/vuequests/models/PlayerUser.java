package dev.veryue.vuequests.models;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.core.UUIDUtil;

import java.util.Set;
import java.util.UUID;

public class PlayerUser extends User {

    private static final Codec<Set<UUID>> UUID_SET_CODEC = UUIDUtil.STRING_CODEC.listOf()
            .xmap(Set::copyOf, ids -> ids.stream().sorted().toList());

    public static final Codec<PlayerUser> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(PlayerUser::getUuid),
            UUID_SET_CODEC.fieldOf("completed_quests").forGetter(PlayerUser::getCompletedQuests),
            UUID_SET_CODEC.fieldOf("completed_objectives").forGetter(PlayerUser::getCompletedObjectives),
            UUID_SET_CODEC.fieldOf("claimed_rewards").forGetter(PlayerUser::getClaimedRewards)
    ).apply(instance, PlayerUser::new));

    public PlayerUser(UUID uuid) {
        super(uuid);
    }

    public PlayerUser(UUID uuid, Set<UUID> completedQuests, Set<UUID> completedObjectives, Set<UUID> claimedRewards) {
        super(uuid, completedQuests, completedObjectives, claimedRewards);
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
