package dev.veryue.vuequests.data.quest;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.UUID;

public class FlatFileQuestLoader extends QuestLoader {

    public static final String RESOURCE_DIRECTORY = VeryUEConstants.MOD_ID + "/sections";

    @Override
    public QuestData loadQuests(ResourceManager resourceManager, HolderLookup.Provider registryLookup) {
        RegistryOps<JsonElement> ops = registryLookup.createSerializationContext(JsonOps.INSTANCE);
        ImmutableMap.Builder<UUID, Section> sections = ImmutableMap.builder();
        ImmutableMap.Builder<UUID, Quest> quests = ImmutableMap.builder();
        FileToIdConverter files = FileToIdConverter.json(RESOURCE_DIRECTORY);

        for (Map.Entry<ResourceLocation, Resource> entry : files.listMatchingResources(resourceManager).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
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
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load quest section " + entry.getKey(), exception);
            }
        }

        return new QuestData(sections.buildOrThrow(), quests.buildOrThrow());
    }
}
