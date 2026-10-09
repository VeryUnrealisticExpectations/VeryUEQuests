package dev.veryue.vuequests.models.quest;

import net.minecraft.world.item.Item;

public class QuestTask<T> {


    // todo; probably not an item icon, a generic image i think.
    private final Item icon;
    private final QuestType<T> type; // what you'll be doing to complete the quest?

    public QuestTask(Item icon, QuestType<T> type) {
        this.icon = icon;
        this.type = type;
    }
}
