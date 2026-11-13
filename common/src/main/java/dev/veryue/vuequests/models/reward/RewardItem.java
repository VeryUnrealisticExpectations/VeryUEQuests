package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.util.TextUtil;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.UUID;

public class RewardItem extends RewardType<ItemStackTemplate> {

    public static final MapCodec<RewardItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardItem::getRewardId),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(RewardItem::getValue)
    ).apply(instance, RewardItem::new));

    public RewardItem(UUID rewardId, ItemStack stack) {
        this(rewardId, ItemStackTemplate.fromNonEmptyStack(stack));
    }

    public RewardItem(UUID rewardId, ItemStackTemplate stack) {
        super(rewardId, stack);
    }

    public ItemStack getStack() {
        ItemStackTemplate template = getValue();
        return new ItemStack(template.item(), template.count(), template.components());
    }

    @Override
    public ItemStack getIconStack() {
        return getStack();
    }

    @Override
    public Component getIconLabel() {
        return Component.literal(TextUtil.abbreviateCount(getStack().getCount()));
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
    public boolean tryGiveReward(ServerPlayer player) {
        return tryAddToInventory(player.getInventory());
    }

    boolean tryAddToInventory(Inventory inventory) {
        ItemStack stack = getStack();
        if (stack.isEmpty()) {
            return false;
        }

        // Share untouched stacks with the live inventory and replace only the slots that would change.
        Inventory staged = new Inventory(inventory.player, new EntityEquipment());
        staged.replaceWith(inventory);
        int remaining = stack.getCount();
        while (remaining > 0) {
            int slot = staged.getSlotWithRemainingSpace(stack);
            if (slot < 0) {
                slot = staged.getFreeSlot();
            }
            if (slot < 0) {
                return false;
            }
            int count = staged.getItem(slot).getCount();
            int added = Math.min(remaining, staged.getMaxStackSize(stack) - count);
            if (added <= 0) {
                return false;
            }
            ItemStack updated = stack.copyWithCount(count + added);
            updated.setPopTime(Inventory.POP_TIME_DURATION);
            staged.setItem(slot, updated);
            remaining -= added;
        }

        inventory.replaceWith(staged);
        inventory.setChanged();
        return true;
    }
}
