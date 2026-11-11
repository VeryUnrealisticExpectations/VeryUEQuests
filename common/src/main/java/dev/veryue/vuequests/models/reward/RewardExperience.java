package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.util.TextUtil;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class RewardExperience extends RewardType<Integer> {

    public static final MapCodec<RewardExperience> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardExperience::getRewardId),
            Codec.INT.fieldOf("experience").forGetter(RewardExperience::getValue)
    ).apply(instance, RewardExperience::new));

    public RewardExperience(UUID rewardId, Integer value) {
        super(rewardId, value);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public Component getIconLabel() {
        return Component.literal(TextUtil.abbreviateCount(getValue()));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.vuequests.reward.experience", getValue());
    }

    @Override
    public RewardTypeSerializer getType() {
        return RewardTypes.EXPERIENCE;
    }

    @Override
    public boolean tryGiveReward(ServerPlayer player) {
        player.giveExperiencePoints(getValue());
        return true;
    }
}
