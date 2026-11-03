package dev.veryue.vuequests.registry;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.editor.api.models.JsonSchema;
import dev.veryue.vuequests.editor.api.QuestApiSchemas;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.reward.RewardExperience;
import dev.veryue.vuequests.models.reward.RewardItem;
import dev.veryue.vuequests.models.reward.RewardType;
import dev.veryue.vuequests.models.reward.RewardTypeSerializer;
import net.minecraft.resources.ResourceLocation;

public class RewardTypes {

    public static final RewardTypeSerializer ITEM = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "item"),
            RewardItem.CODEC, QuestApiSchemas.reward("vuequests:item", "item", QuestApiSchemas.itemStack()));
    public static final RewardTypeSerializer EXPERIENCE = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "experience"),
            RewardExperience.CODEC, QuestApiSchemas.reward("vuequests:experience", "experience", JsonSchema.type("integer")));

    public static RewardTypeSerializer register(String name, MapCodec<? extends RewardType<?>> codec) {
        return register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, name), codec);
    }

    public static RewardTypeSerializer register(ResourceLocation id, MapCodec<? extends RewardType<?>> codec) {
        return register(id, codec, null);
    }

    public static RewardTypeSerializer register(ResourceLocation id, MapCodec<? extends RewardType<?>> codec, JsonSchema schema) {
        return VeryUERegistries.register(VeryUERegistries.REWARD_TYPES, id, new RewardTypeSerializer(codec, schema));
    }

    public static void init() {}
}
