package dev.veryue.vuequests.models.quest;

import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;

// quests are what get loaded into the UI


// todo;m maybe a record
public class Quest {

    // todo; we should version the quests for the editor, so they can be exported or something


    // location in ui i think
    private final int x;
    private final int y;

    // todo; these should probably be localizable.
    private final String title;
    private final String subTitle;
    private final String description;
    private final Item icon;
    private final String tooltip;

    // todo; not sure
    private final UUID sectionUUID;



    private final UUID questId;
    private final List<QuestObjective<?>> objectives;
    private final List<RewardType<?>> rewards;

    // todo; how will we handle dependencies? maybe just by the UUID of the quest not sure.
    private final List<Quest> dependencies;

    public Quest(int x, int y, String title, String subTitle, String description, Item icon, String tooltip, UUID sectionUUID, UUID questId, List<QuestObjective<?>> objectives, List<RewardType<?>> rewards, List<Quest> dependencies) {
        this.x = x;
        this.y = y;
        this.title = title;
        this.subTitle = subTitle;
        this.description = description;
        this.icon = icon;
        this.tooltip = tooltip;
        this.sectionUUID = sectionUUID;
        this.questId = questId;
        this.objectives = objectives;
        this.rewards = rewards;
        this.dependencies = dependencies;
    }
    // todo; what about circular dependencies? does it matter? only if we're traversing maybe.


    public void completeQuest(User user) {
        // todo; we have to:
        //  give the user the reward --- not even that, we have to let the user be ABLE to claim the rewards
        //  mark the quest as complete
        //
        user.grantQuestCompletion(this);

        user.handleReward(this); // todo; do we do it this way?


    }




}
