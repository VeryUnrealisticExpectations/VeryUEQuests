package dev.veryue.vuequests.data.quest;

import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;

import java.util.Map;
import java.util.UUID;

public record QuestData(Map<UUID, Section> sections, Map<UUID, Quest> quests) {
    public static final QuestData EMPTY = new QuestData(Map.of(), Map.of());
}
