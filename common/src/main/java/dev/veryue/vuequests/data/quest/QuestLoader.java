package dev.veryue.vuequests.data.quest;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Map;
import java.util.UUID;

public abstract class QuestLoader {

    public abstract QuestData loadQuests(ResourceManager resourceManager, HolderLookup.Provider registryLookup);

    protected QuestData decodeSections(Map<?, ? extends JsonElement> definitions, HolderLookup.Provider registryLookup) {
        var ops = registryLookup.createSerializationContext(JsonOps.INSTANCE);
        ImmutableMap.Builder<UUID, Section> sections = ImmutableMap.builder();
        ImmutableMap.Builder<UUID, Quest> quests = ImmutableMap.builder();
        for (var entry : definitions.entrySet()) {
            try {
                JsonElement json = entry.getValue();
                String modLoaded = Section.MOD_LOADED_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
                if (!modLoaded.isEmpty() && !PlatformHelper.INSTANCE.isModLoaded(modLoaded)) {
                    continue;
                }
                Section section = Section.CODEC.parse(ops, json).getOrThrow();
                if (section.disabled()) {
                    continue;
                }
                UUID sectionId = section.sectionUUID();
                sections.put(sectionId, section);
                for (Quest quest : section.questList()) {
                    if (!quest.sectionUUID().equals(sectionId)) {
                        throw new IllegalArgumentException("Quest " + quest.questId() + " belongs to a different section");
                    }
                    quests.put(quest.questId(), quest);
                }
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Could not load quest section " + entry.getKey(), exception);
            }
        }
        return new QuestData(sections.buildOrThrow(), quests.buildOrThrow());
    }

}
