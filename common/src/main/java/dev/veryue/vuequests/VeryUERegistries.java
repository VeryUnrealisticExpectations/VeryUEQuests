package dev.veryue.vuequests;

import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import dev.veryue.vuequests.models.reward.RewardTypeSerializer;
import dev.veryue.vuequests.platform.RegistryHelper;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class VeryUERegistries {

    public static final ResourceKey<Registry<RewardTypeSerializer>> REWARD_TYPE_KEY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "reward_type"));

    public static final ResourceKey<Registry<QuestObjectiveSerializer>> QUEST_OBJECTIVE_TYPE_KEY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "quest_objective_type"));

    public static final Registry<RewardTypeSerializer> REWARD_TYPES = createRegistry(REWARD_TYPE_KEY);
    public static final Registry<QuestObjectiveSerializer> QUEST_OBJECTIVE_TYPES = createRegistry(QUEST_OBJECTIVE_TYPE_KEY);

    public static <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) {
        return RegistryHelper.INSTANCE.createRegistry(key);
    }

    public static <T> T register(Registry<T> registry, ResourceLocation id, T value) {
        return RegistryHelper.INSTANCE.register(registry, id, value);
    }

    public static void init() {
        RewardTypes.init();
        QuestObjectiveTypes.init();
    }
}
