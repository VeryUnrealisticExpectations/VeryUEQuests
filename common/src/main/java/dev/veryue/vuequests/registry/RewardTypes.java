package dev.veryue.vuequests.registry;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.editor.api.QuestApiSchemas;
import dev.veryue.vuequests.editor.api.models.JsonSchema;
import dev.veryue.vuequests.models.reward.RewardAdvancement;
import dev.veryue.vuequests.models.reward.RewardAttribute;
import dev.veryue.vuequests.models.reward.RewardCommand;
import dev.veryue.vuequests.models.reward.RewardExperience;
import dev.veryue.vuequests.models.reward.RewardExperienceLevels;
import dev.veryue.vuequests.models.reward.RewardItem;
import dev.veryue.vuequests.models.reward.RewardLootTable;
import dev.veryue.vuequests.models.reward.RewardType;
import dev.veryue.vuequests.models.reward.RewardTypeSerializer;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class RewardTypes {

    public static final RewardTypeSerializer ITEM = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "item"),
            RewardItem.CODEC, QuestApiSchemas.reward("vuequests:item", "item", QuestApiSchemas.itemStack()));
    public static final RewardTypeSerializer EXPERIENCE = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "experience"),
            RewardExperience.CODEC, QuestApiSchemas.reward("vuequests:experience", "experience", JsonSchema.type("integer")));
    public static final RewardTypeSerializer EXPERIENCE_LEVELS = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "experience_levels"),
            RewardExperienceLevels.CODEC, QuestApiSchemas.reward("vuequests:experience_levels", "levels",
                    JsonSchema.type("integer").minimum(1).maximum(Integer.MAX_VALUE)));
    public static final RewardTypeSerializer ADVANCEMENT = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "advancement"),
            RewardAdvancement.CODEC, QuestApiSchemas.reward("vuequests:advancement", "advancement",
                    JsonSchema.type("string").description("Loaded advancement ID.")));
    public static final RewardTypeSerializer LOOT_TABLE = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "loot_table"),
            RewardLootTable.CODEC, QuestApiSchemas.reward("vuequests:loot_table", "loot_table",
                    JsonSchema.type("string").description("Loaded loot table ID. Rolls with chest loot context.")));
    public static final RewardTypeSerializer COMMAND = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "command"),
            RewardCommand.CODEC, QuestApiSchemas.reward("vuequests:command", "command",
                    JsonSchema.type("string").description("Single command executed as the claimant at permission level 2. Supports @s and other selectors.")));
    public static final RewardTypeSerializer ATTRIBUTE = register(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, "attribute"),
            RewardAttribute.CODEC, QuestApiSchemas.reward("vuequests:attribute", Map.of(
                    "attribute", JsonSchema.type("string").description("Registered player attribute ID."),
                    "amount", JsonSchema.type("number").description("Finite positive additive modifier restored from the player's reward claim."))));

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
