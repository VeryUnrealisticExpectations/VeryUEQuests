package dev.veryue.vuequests.client;

import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClientQuestData {


    // TODO: temporary location for client data, realistically should re-use the QuestReloadDelegate so if it's single player
    //  we aren't duping the data

    private static QuestData data = QuestData.EMPTY;

    public static void update(List<Section> sections) {
        Map<UUID, Section> sectionIndex = new HashMap<>();
        Map<UUID, Quest> questIndex = new HashMap<>();
        for (Section section : sections) {
            sectionIndex.put(section.sectionUUID(), section);
            for (Quest quest : section.questList()) {
                questIndex.put(quest.questId(), quest);
            }
        }
        data = new QuestData((sectionIndex), (questIndex));
    }

    public static QuestData getData() {
        return data;
    }

    public static void clear() {
        data = QuestData.EMPTY;
    }
}
