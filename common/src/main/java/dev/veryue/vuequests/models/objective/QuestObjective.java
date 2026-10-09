package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.Codec;
import dev.veryue.vuequests.VeryUERegistries;
import net.minecraft.world.item.Item;

import java.util.UUID;

public abstract class QuestObjective<T> {

    public static final Codec<QuestObjective<?>> CODEC = Codec.lazyInitialized(() -> VeryUERegistries.QUEST_OBJECTIVE_TYPES
            .byNameCodec()
            .dispatch("type", QuestObjective::getType, QuestObjectiveSerializer::codec));

    private final UUID objectiveId;
    // todo; probably not an item icon, a generic image? maybe
    private final Item icon;
    private final T value;

    public QuestObjective(UUID objectiveId, Item icon, T value) {
        this.objectiveId = objectiveId;
        this.icon = icon;
        this.value = value;
    }

    public UUID getObjectiveId() {
        return objectiveId;
    }

    public Item getIcon() {
        return icon;
    }

    public T getValue() {
        return value;
    }

    public abstract QuestObjectiveSerializer getType();
}
