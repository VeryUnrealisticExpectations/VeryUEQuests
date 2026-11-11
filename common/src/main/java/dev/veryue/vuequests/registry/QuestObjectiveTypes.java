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
import dev.veryue.vuequests.models.objective.ObjectiveSubmitItem;
import dev.veryue.vuequests.models.objective.ObjectiveExperienceLevels;
import dev.veryue.vuequests.models.objective.ObjectiveAcknowledge;
import dev.veryue.vuequests.models.objective.ObjectiveTravelDimension;
import dev.veryue.vuequests.models.objective.ObjectiveTravelBiome;
import dev.veryue.vuequests.models.objective.ObjectiveTravelCoordinates;
import dev.veryue.vuequests.models.objective.ObjectiveTravelStructure;
import dev.veryue.vuequests.models.objective.ObjectiveStatistic;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

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
    public static final QuestObjectiveSerializer<Integer> SUBMIT_ITEM = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "submit_item"),
            ObjectiveSubmitItem.CODEC, Codec.INT, QuestApiSchemas.countedObjective("vuequests:submit_item", "item",
                    QuestApiSchemas.itemStack(), "Total items to submit by clicking. Defaults to item.count, then 1."));
    public static final QuestObjectiveSerializer<Integer> EXPERIENCE_LEVELS = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "experience_levels"),
            ObjectiveExperienceLevels.CODEC, Codec.INT, QuestApiSchemas.objective("vuequests:experience_levels", "levels",
                    JsonSchema.type("integer").minimum(1).maximum(Integer.MAX_VALUE).description("Required player level; does not consume XP.")));
    public static final QuestObjectiveSerializer<Boolean> TRAVEL_DIMENSION = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "travel_dimension"),
            ObjectiveTravelDimension.CODEC, Codec.BOOL, QuestApiSchemas.objective("vuequests:travel_dimension", "dimension",
                    JsonSchema.type("string").description("Loaded dimension ID.")));
    public static final QuestObjectiveSerializer<Boolean> TRAVEL_BIOME = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "travel_biome"),
            ObjectiveTravelBiome.CODEC, Codec.BOOL, QuestApiSchemas.objective("vuequests:travel_biome", "biome",
                    JsonSchema.type("string").description("Loaded minecraft:worldgen/biome registry ID.")));
    public static final QuestObjectiveSerializer<Boolean> TRAVEL_COORDINATES = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "travel_coordinates"),
            ObjectiveTravelCoordinates.CODEC, Codec.BOOL, QuestApiSchemas.objective("vuequests:travel_coordinates", Map.of(
                    "dimension", JsonSchema.type("string").description("Dimension containing the block box."),
                    "min_x", JsonSchema.type("integer"), "min_y", JsonSchema.type("integer"), "min_z", JsonSchema.type("integer"),
                    "max_x", JsonSchema.type("integer"), "max_y", JsonSchema.type("integer"), "max_z", JsonSchema.type("integer")))
                    .description("Inclusive integer block bounds; each minimum must be at most its maximum."));
    public static final QuestObjectiveSerializer<Boolean> TRAVEL_STRUCTURE = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "travel_structure"),
            ObjectiveTravelStructure.CODEC, Codec.BOOL, QuestApiSchemas.objective("vuequests:travel_structure", "structure",
                    JsonSchema.type("string").description("Loaded minecraft:worldgen/structure registry ID.")));
    public static final QuestObjectiveSerializer<Integer> STATISTIC = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "statistic"),
            ObjectiveStatistic.CODEC, Codec.INT, statisticSchema());
    public static final QuestObjectiveSerializer<Boolean> ACKNOWLEDGEMENT = register(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "acknowledgement"),
            ObjectiveAcknowledge.CODEC, Codec.BOOL, QuestApiSchemas.objective("vuequests:acknowledgement", Map.of()));

    private static JsonSchema statisticSchema() {
        JsonSchema schema = QuestApiSchemas.objective("vuequests:statistic", Map.of(
                "stat_type", JsonSchema.type("string").description("Loaded minecraft:stat_type ID, for example minecraft:custom."),
                "statistic", JsonSchema.type("string").description("Value ID from the chosen statistic type's registry.")));
        schema.properties.put("count", JsonSchema.type("integer").minimum(1).maximum(Integer.MAX_VALUE).description("Target statistic value; defaults to 1."));
        return schema;
    }

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
