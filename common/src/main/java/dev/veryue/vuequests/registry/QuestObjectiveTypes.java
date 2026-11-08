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
