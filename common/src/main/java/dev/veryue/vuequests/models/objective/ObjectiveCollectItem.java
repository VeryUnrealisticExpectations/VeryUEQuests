package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ObjectiveCollectItem extends QuestObjective<ItemStack, Integer> {

    public static final MapCodec<ObjectiveCollectItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveCollectItem::getObjectiveId),
            ItemStack.CODEC.fieldOf("item").forGetter(ObjectiveCollectItem::getStack)
            // todo; likely need to add a separate count since we're limited to 99 i think
    ).apply(instance, ObjectiveCollectItem::new));

    public ObjectiveCollectItem(UUID objectiveId, ItemStack stack) {
        super(objectiveId, stack);
    }

    @Deprecated
    public ObjectiveCollectItem(UUID objectiveId, Item ignoredIcon, ItemStack stack) {
        this(objectiveId, stack);
    }

    public ItemStack getStack() {
        return getValue();
    }

    @Override
    public ItemStack getIconStack() {
        return getStack().copy();
    }

    @Override
    public Component getActionText() {
        ItemStack stack = getStack();
        return Component.translatable("screen.vuequests.objective.collect", stack.getCount(), stack.getHoverName());
    }

    @Override
    protected int getProgressTarget() {
        return getStack().getCount();
    }

    @Override
    protected int getProgressCount(@Nullable Integer progress) {
        return progress == null ? 0 : progress;
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<ItemStack, Integer> objectiveProgress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack requiredStack = getStack();
        int currentProgress = objectiveProgress.getCurrentProgress() == null ? 0 : objectiveProgress.getCurrentProgress();
        if (currentProgress >= requiredStack.getCount()) {
            return;
        }

        if (inventory.findSlotMatchingItem(requiredStack) < 0) {
            return;
        }

        int itemCount = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(requiredStack, stack)) {
                itemCount += stack.getCount();
            }
        }

        if (itemCount > currentProgress) {
            objectiveProgress.setCurrentProgress(Math.min(itemCount, requiredStack.getCount()));
        }
    }

    @Override
    public boolean isComplete(QuestObjectiveProgress<ItemStack, Integer> progress) {
        return progress.getCurrentProgress() != null && progress.getCurrentProgress() >= getValue().getCount();
    }

    @Override
    public QuestObjectiveSerializer<Integer> getType() {
        return QuestObjectiveTypes.COLLECT_ITEM;
    }
}
