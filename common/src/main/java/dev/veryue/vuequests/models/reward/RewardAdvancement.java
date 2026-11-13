package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class RewardAdvancement extends RewardType<Identifier> {

    public static final MapCodec<RewardAdvancement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardAdvancement::getRewardId),
            Identifier.CODEC.fieldOf("advancement").forGetter(RewardAdvancement::getValue)
    ).apply(instance, RewardAdvancement::new));

    public RewardAdvancement(UUID rewardId, Identifier advancement) {
        super(rewardId, advancement);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.vuequests.reward.advancement", getValue().toString());
    }

    @Override
    public RewardTypeSerializer getType() {
        return RewardTypes.ADVANCEMENT;
    }

    @Override
    public boolean tryGiveReward(ServerPlayer player) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(getValue());
        if (advancement == null) {
            return false;
        }
        var advancements = player.getAdvancements();
        var progress = advancements.getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            advancements.award(advancement, criterion);
        }
        return progress.isDone();
    }
}
