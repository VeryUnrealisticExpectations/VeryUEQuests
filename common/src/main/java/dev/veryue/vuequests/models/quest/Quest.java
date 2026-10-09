package dev.veryue.vuequests.models.quest;

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
    private final String decsription;
    private final Item icon;
    private final String tooltip;

    // todo; not sure
    private final UUID sectionUUID;



    private final UUID uuid;
    private final List<QuestTask<?>> tasks;
    private final List<RewardType<?>> rewards;

    // todo; how will we handle dependencies?
    private final List<Quest> dependencies;

    public Quest(int x, int y, String title, String subTitle, String decsription, Item icon, String tooltip, UUID sectionUUID, UUID uuid, List<QuestTask<?>> tasks, List<RewardType<?>> rewards, List<Quest> dependencies) {
        this.x = x;
        this.y = y;
        this.title = title;
        this.subTitle = subTitle;
        this.decsription = decsription;
        this.icon = icon;
        this.tooltip = tooltip;
        this.sectionUUID = sectionUUID;
        this.uuid = uuid;
        this.tasks = tasks;
        this.rewards = rewards;
        this.dependencies = dependencies;
    }
    // todo; what about circular dependencies? does it matter? only if we're traversing maybe.







}
