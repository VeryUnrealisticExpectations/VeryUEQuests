package dev.veryue.vuequests.models.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;


/**
 * @param display     todo; these should probably be localizable.
 * @param sectionUUID todo; not sure
 */
public record Quest(QuestPosition position, QuestDisplay display, UUID sectionUUID, UUID questId,
                    List<QuestObjective<?, ?>> objectives, List<RewardType<?>> rewards, List<UUID> dependencies) {

    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            QuestPosition.CODEC.fieldOf("position").forGetter(Quest::position),
            QuestDisplay.CODEC.fieldOf("display").forGetter(Quest::display),
            UUIDUtil.STRING_CODEC.fieldOf("section_id").forGetter(Quest::sectionUUID),
            UUIDUtil.STRING_CODEC.fieldOf("quest_id").forGetter(Quest::questId),
            QuestObjective.CODEC.listOf().optionalFieldOf("objectives", List.of()).forGetter(Quest::objectives),
            RewardType.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("dependencies", List.of()).forGetter(Quest::dependencies)
    ).apply(instance, Quest::new));

    // todo; we should version the quests for the editor, so they can be exported or something


    public void completeQuest(User user) {
        // todo; we have to:
        //  give the user the reward --- not even that, we have to let the user be ABLE to claim the rewards
        //  mark the quest as complete
        //
        user.grantQuestCompletion(this);

        user.handleReward(this); // todo; do we do it this way?


    }


}
