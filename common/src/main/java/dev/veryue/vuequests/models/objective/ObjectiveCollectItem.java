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
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Optional;
import java.util.UUID;

public class ObjectiveCollectItem extends CountedQuestObjective<ItemStackTemplate> {

    public static final MapCodec<ObjectiveCollectItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveCollectItem::getObjectiveId),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(ObjectiveCollectItem::getValue),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(objective -> Optional.of(objective.getCount()))
    ).apply(instance, (id, stack, count) -> new ObjectiveCollectItem(id, stack, count.orElse(stack.count()))));

    public ObjectiveCollectItem(UUID objectiveId, ItemStack stack) {
        this(objectiveId, stack, stack.getCount());
    }

    public ObjectiveCollectItem(UUID objectiveId, ItemStack stack, int count) {
        this(objectiveId, ItemStackTemplate.fromNonEmptyStack(stack), count);
    }

    public ObjectiveCollectItem(UUID objectiveId, ItemStackTemplate stack, int count) {
        super(objectiveId, stack.withCount(1), count);
    }

    @Deprecated
    public ObjectiveCollectItem(UUID objectiveId, Item ignoredIcon, ItemStack stack) {
        this(objectiveId, stack);
    }

    public ItemStack getStack() {
        return getValue().create();
    }

    @Override
    public ItemStack getIconStack() {
        return getStack().copy();
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.collect", getCount(), getStack().getHoverName());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<ItemStackTemplate, Integer> objectiveProgress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack requiredStack = getStack();
        int count = getCount();
        int currentProgress = getProgressCount(objectiveProgress.getCurrentProgress());
        if (currentProgress >= count) {
            return;
        }

        if (inventory.findSlotMatchingItem(requiredStack) < 0) {
            return;
        }

        int itemCount = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && itemCount < count; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(requiredStack, stack)) {
                itemCount += Math.min(count - itemCount, stack.getCount());
            }
        }

        if (itemCount > currentProgress) {
            objectiveProgress.setCurrentProgress(itemCount);
        }
    }

    @Override
    public QuestObjectiveSerializer<Integer> getType() {
        return QuestObjectiveTypes.COLLECT_ITEM;
    }
}
