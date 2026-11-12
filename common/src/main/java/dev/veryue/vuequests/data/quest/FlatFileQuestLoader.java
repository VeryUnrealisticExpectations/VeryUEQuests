package dev.veryue.vuequests.data.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.veryue.vuequests.VeryUEConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public class FlatFileQuestLoader extends QuestLoader {

    public static final String RESOURCE_DIRECTORY = VeryUEConstants.MOD_ID + "/sections";

    @Override
    public QuestData loadQuests(ResourceManager resourceManager, HolderLookup.Provider registryLookup) {
        Map<ResourceLocation, JsonElement> definitions = new LinkedHashMap<>();
        FileToIdConverter files = FileToIdConverter.json(RESOURCE_DIRECTORY);

        for (Map.Entry<ResourceLocation, Resource> entry : files.listMatchingResources(resourceManager).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                definitions.put(entry.getKey(), JsonParser.parseReader(reader));
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load quest section " + entry.getKey(), exception);
            }
        }

        return decodeSections(definitions, registryLookup);
    }
}
