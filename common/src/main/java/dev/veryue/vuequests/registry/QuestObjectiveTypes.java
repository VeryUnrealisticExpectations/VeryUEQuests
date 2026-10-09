package dev.veryue.vuequests.registry;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.objective.ObjectiveCollectItem;
import dev.veryue.vuequests.models.objective.ObjectiveEntity;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import net.minecraft.resources.ResourceLocation;

public class QuestObjectiveTypes {

    public static final QuestObjectiveSerializer COLLECT_ITEM = register("collect_item", ObjectiveCollectItem.CODEC);
    public static final QuestObjectiveSerializer ENTITY = register("entity", ObjectiveEntity.CODEC);

    public static QuestObjectiveSerializer register(String name, MapCodec<? extends QuestObjective<?>> codec) {
        return register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, name), codec);
    }

    public static QuestObjectiveSerializer register(ResourceLocation id, MapCodec<? extends QuestObjective<?>> codec) {
        return VeryUERegistries.register(VeryUERegistries.QUEST_OBJECTIVE_TYPES, id, new QuestObjectiveSerializer(codec));
    }

    public static void init() {}
}
