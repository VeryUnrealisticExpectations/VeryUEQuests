package dev.veryue.vuequests.client;

import com.google.common.collect.ImmutableMap;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ClientQuestData {

    private static QuestData data = QuestData.EMPTY;
    @Nullable
    private static PlayerUser user;

    public static void update(List<Section> sections, PlayerUser playerUser) {
        if (hasIntegratedServer()) {
            data = QuestData.EMPTY;
            user = playerUser;
            return;
        }

        ImmutableMap.Builder<UUID, Section> sectionIndex = ImmutableMap.builder();
        ImmutableMap.Builder<UUID, Quest> questIndex = ImmutableMap.builder();
        for (Section section : sections) {
            sectionIndex.put(section.sectionUUID(), section);
            for (Quest quest : section.questList()) {
                questIndex.put(quest.questId(), quest);
            }
        }
        data = new QuestData(sectionIndex.buildOrThrow(), questIndex.buildOrThrow());
        user = playerUser;
    }

    public static QuestData getData() {
        return hasIntegratedServer() ? QuestReloadDelegate.INSTANCE.getData() : data;
    }

    private static boolean hasIntegratedServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getSingleplayerServer() != null;
    }

    @Nullable
    public static PlayerUser getUser() {
        return user;
    }

    public static boolean prerequisitesMet(Quest quest) {
        return user == null ? quest.dependencies().isEmpty() : getData().prerequisitesMet(quest.questId(), user);
    }

    public static void clear() {
        data = QuestData.EMPTY;
        user = null;
    }
}
