package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class RewardExperienceLevels extends RewardType<Integer> {

    public static final MapCodec<RewardExperienceLevels> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardExperienceLevels::getRewardId),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("levels").forGetter(RewardExperienceLevels::getValue)
    ).apply(instance, RewardExperienceLevels::new));

    public RewardExperienceLevels(UUID rewardId, int levels) {
        super(rewardId, levels);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }


    @Override
    public Component getIconLabel() {
        return Component.literal(Integer.toString(getValue()));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.vuequests.reward.experience_levels", getValue());
    }

    @Override
    public RewardTypeSerializer getType() {
        return RewardTypes.EXPERIENCE_LEVELS;
    }

    @Override
    public boolean tryGiveReward(ServerPlayer player) {
        if (getValue() > Integer.MAX_VALUE - player.experienceLevel) {
            return false;
        }
        player.giveExperienceLevels(getValue());
        return true;
    }
}
