package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class RewardItem extends RewardType<ItemStack> {

    public static final MapCodec<RewardItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardItem::getRewardId),
            ItemStack.CODEC.fieldOf("item").forGetter(RewardItem::getStack)
    ).apply(instance, RewardItem::new));

    public RewardItem(UUID rewardId, ItemStack stack) {
        super(rewardId, stack);
    }

    public ItemStack getStack() {
        return getValue();
    }

    @Override
    public ItemStack getIconStack() {
        return getStack();
    }

    @Override
    public Component getDisplayName() {
        ItemStack stack = getStack();
        return Component.translatable("screen.vuequests.reward.item", stack.getCount(), stack.getHoverName());
    }

    @Override
    public RewardTypeSerializer getType() {
        return RewardTypes.ITEM;
    }

    @Override
    public void giveReward(User user) {
    }
}
