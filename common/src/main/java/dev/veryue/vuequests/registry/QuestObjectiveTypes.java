package dev.veryue.vuequests.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.editor.api.models.JsonSchema;
import dev.veryue.vuequests.editor.api.QuestApiSchemas;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.objective.ObjectiveCollectItem;
import dev.veryue.vuequests.models.objective.ObjectiveGetAdvancement;
import dev.veryue.vuequests.models.objective.ObjectiveSlayEntity;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import net.minecraft.resources.ResourceLocation;

public class QuestObjectiveTypes {

    public static final QuestObjectiveSerializer<Integer> COLLECT_ITEM = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "collect_item"),
            ObjectiveCollectItem.CODEC, Codec.INT, QuestApiSchemas.countedObjective("vuequests:collect_item", "item",
                    QuestApiSchemas.itemStack(), "Required item quantity. Defaults to the legacy item.count, then 1."));
    public static final QuestObjectiveSerializer<Integer> ENTITY = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "entity"),
            ObjectiveSlayEntity.CODEC, Codec.INT, QuestApiSchemas.countedObjective("vuequests:entity", "entity",
                    JsonSchema.type("string").description("Loaded minecraft:entity_type registry ID."),
                    "Required kills of this entity type. Defaults to 1."));
    public static final QuestObjectiveSerializer<Boolean> ADVANCEMENT = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "advancement"),
            ObjectiveGetAdvancement.CODEC, Codec.BOOL, QuestApiSchemas.objective("vuequests:advancement", "advancement",
                    JsonSchema.type("string").description("Datapack advancement ID, such as minecraft:story/mine_stone.")));
    // TODO: ITEM SUBMISSION TYPE. This will take the item from your inventory. It will be a counting one, it'll take an itemstack
    // TODO: XP Levels: - The player will be required to be a certain level to complete, also a counting one.
    // TODO: Travel to Dimension : The player will visit a dimension to complete the objective auto completion will be base on the loaded dimensions I think this is a true/false one. It becomes true once the player visits the dimension
    // TODO: Travel to Biome : The player will go to a biome to complete the objective auto completion will be based on the loaded biomes I think this is a true/false one. it becomes true once the player visits the biome
    // TODO: Travel to Coordinates: The player will go to a set of coordinates to complete the objective. This can be a 'box' so a minX, minY, minZ, maxX, maxY, maxZ.
    // TODO: Travel to Structure: The player will go to a STRUCTURE these are generally i think mineshafts, villages, strongholds
    // TODO: Achieve Statistic: The player will achieve a certain number in a statistic. Auto completion is the statistic, this is a counting one (integer) based.
    // TODO: Acknowledgement: The player acknowledges the quest, and it gets marked as completed.
    

    public static <P> QuestObjectiveSerializer<P> register(String name, MapCodec<? extends QuestObjective<?, P>> codec) {
        return register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, name), codec);
    }

    public static <P> QuestObjectiveSerializer<P> register(ResourceLocation id, MapCodec<? extends QuestObjective<?, P>> codec) {
        return register(id, codec, null);
    }

    public static <P> QuestObjectiveSerializer<P> register(ResourceLocation id, MapCodec<? extends QuestObjective<?, P>> codec, JsonSchema schema) {
        QuestObjectiveSerializer<P> serializer = new QuestObjectiveSerializer<>(codec, schema);
        VeryUERegistries.register(VeryUERegistries.QUEST_OBJECTIVE_TYPES, id, serializer);
        return serializer;
    }

    public static <P> QuestObjectiveSerializer<P> register(ResourceLocation id, MapCodec<? extends QuestObjective<?, P>> codec, Codec<P> progressCodec, JsonSchema schema) {
        QuestObjectiveSerializer<P> serializer = new QuestObjectiveSerializer<>(codec, progressCodec, schema);
        VeryUERegistries.register(VeryUERegistries.QUEST_OBJECTIVE_TYPES, id, serializer);
        return serializer;
    }

    public static void init() {}
}
