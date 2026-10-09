package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ObjectiveCollectItem extends QuestObjective<ItemStack> {

    public static final MapCodec<ObjectiveCollectItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveCollectItem::getObjectiveId),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(ObjectiveCollectItem::getIcon),
            ItemStack.CODEC.fieldOf("item").forGetter(ObjectiveCollectItem::getStack)
    ).apply(instance, ObjectiveCollectItem::new));

    public ObjectiveCollectItem(UUID objectiveId, Item icon, ItemStack stack) {
        super(objectiveId, icon, stack);
    }

    public ItemStack getStack() {
        return getValue();
    }

    @Override
    public QuestObjectiveSerializer getType() {
        return QuestObjectiveTypes.COLLECT_ITEM;
    }
}
