package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.Codec;
import dev.veryue.vuequests.VeryUERegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public abstract class QuestObjective<T> {

    public static final Codec<QuestObjective<?>> CODEC = Codec.lazyInitialized(() -> VeryUERegistries.QUEST_OBJECTIVE_TYPES
            .byNameCodec()
            .dispatch("type", QuestObjective::getType, QuestObjectiveSerializer::codec));

    private final UUID objectiveId;
    private final T value;

    public QuestObjective(UUID objectiveId, T value) {
        this.objectiveId = objectiveId;
        this.value = value;
    }

    @Deprecated
    public QuestObjective(UUID objectiveId, Item ignoredIcon, T value) {
        this(objectiveId, value);
    }

    public UUID getObjectiveId() {
        return objectiveId;
    }

    public Item getIcon() {
        return getIconStack().getItem();
    }

    public ItemStack getIconStack() {
        return new ItemStack(Items.PAPER);
    }

    public T getValue() {
        return value;
    }

    public abstract QuestObjectiveSerializer getType();
}
