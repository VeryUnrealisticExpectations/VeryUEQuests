package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ObjectiveCollectItem extends QuestObjective<ItemStack, Integer> {

    public static final MapCodec<ObjectiveCollectItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveCollectItem::getObjectiveId),
            ItemStack.CODEC.fieldOf("item").forGetter(ObjectiveCollectItem::getStack)
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
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<ItemStack, Integer> objectiveProgress) {
        // todo; look in the player inventory somehow, and check for an item match, if the count is bigger than the current progress, set to the new progress.
    }

    @Override
    public QuestObjectiveSerializer getType() {
        return QuestObjectiveTypes.COLLECT_ITEM;
    }
}
