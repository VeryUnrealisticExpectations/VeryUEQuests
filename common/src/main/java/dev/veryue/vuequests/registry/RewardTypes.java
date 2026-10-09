package dev.veryue.vuequests.registry;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.reward.RewardExperience;
import dev.veryue.vuequests.models.reward.RewardItem;
import dev.veryue.vuequests.models.reward.RewardType;
import dev.veryue.vuequests.models.reward.RewardTypeSerializer;
import net.minecraft.resources.ResourceLocation;

public class RewardTypes {

    public static final RewardTypeSerializer ITEM = register("item", RewardItem.CODEC);
    public static final RewardTypeSerializer EXPERIENCE = register("experience", RewardExperience.CODEC);

    public static RewardTypeSerializer register(String name, MapCodec<? extends RewardType<?>> codec) {
        return register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, name), codec);
    }

    public static RewardTypeSerializer register(ResourceLocation id, MapCodec<? extends RewardType<?>> codec) {
        return VeryUERegistries.register(VeryUERegistries.REWARD_TYPES, id, new RewardTypeSerializer(codec));
    }

    public static void init() {}
}
