package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class ObjectiveSubmitItem extends CountedQuestObjective<ItemStack> {
    public static final MapCodec<ObjectiveSubmitItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveSubmitItem::getObjectiveId),
            Codec.of(ItemStack.SINGLE_ITEM_CODEC, ItemStack.CODEC).fieldOf("item").forGetter(ObjectiveSubmitItem::getValue),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(objective -> Optional.of(objective.getCount()))
    ).apply(instance, (id, stack, count) -> new ObjectiveSubmitItem(id, stack, count.orElse(stack.getCount()))));

    public ObjectiveSubmitItem(UUID objectiveId, ItemStack stack, int count) {
        super(objectiveId, stack.getCount() == 1 ? stack : stack.copyWithCount(1), count);
    }

    @Override
    public boolean requiresInteraction() {
        return true;
    }

    @Override
    public ItemStack getIconStack() {
        return getValue().copy();
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.submit", getCount(), getValue().getHoverName());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<ItemStack, Integer> progress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player == null || !getObjectiveId().equals(context.getParamOrNull(ObjectiveContext.INTERACTION))) {
            return;
        }
        Inventory inventory = player.getInventory();
        int submitted = getProgressCount(progress.getCurrentProgress());
        for (int slot = 0; slot < inventory.getContainerSize() && submitted < getCount(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(getValue(), stack)) {
                int taken = Math.min(getCount() - submitted, stack.getCount());
                stack.shrink(taken);
                submitted += taken;
            }
        }
        progress.setCurrentProgress(submitted);
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
    }

    @Override
    public QuestObjectiveSerializer<Integer> getType() {
        return QuestObjectiveTypes.SUBMIT_ITEM;
    }
}
