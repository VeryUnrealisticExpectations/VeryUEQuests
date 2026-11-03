package dev.veryue.vuequests.registry;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.editor.api.models.JsonSchema;
import dev.veryue.vuequests.editor.api.QuestApiSchemas;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.objective.ObjectiveCollectItem;
import dev.veryue.vuequests.models.objective.ObjectiveEntity;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import net.minecraft.resources.ResourceLocation;

public class QuestObjectiveTypes {

    public static final QuestObjectiveSerializer COLLECT_ITEM = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "collect_item"),
            ObjectiveCollectItem.CODEC, QuestApiSchemas.objective("vuequests:collect_item", "item", QuestApiSchemas.itemStack()));
    public static final QuestObjectiveSerializer ENTITY = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "entity"),
            ObjectiveEntity.CODEC, QuestApiSchemas.objective("vuequests:entity", "entity", JsonSchema.type("string").description("Loaded minecraft:entity_type registry ID.")));

    public static QuestObjectiveSerializer register(String name, MapCodec<? extends QuestObjective<?>> codec) {
        return register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, name), codec);
    }

    public static QuestObjectiveSerializer register(ResourceLocation id, MapCodec<? extends QuestObjective<?>> codec) {
        return register(id, codec, null);
    }

    public static QuestObjectiveSerializer register(ResourceLocation id, MapCodec<? extends QuestObjective<?>> codec, JsonSchema schema) {
        return VeryUERegistries.register(VeryUERegistries.QUEST_OBJECTIVE_TYPES, id, new QuestObjectiveSerializer(codec, schema));
    }

    public static void init() {}
}
