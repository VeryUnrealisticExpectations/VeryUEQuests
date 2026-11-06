package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveEntity extends QuestObjective<EntityType<?>, Integer> {

    public static final MapCodec<ObjectiveEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveEntity::getObjectiveId),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(ObjectiveEntity::getEntityType)
    ).apply(instance, ObjectiveEntity::new));

    public ObjectiveEntity(UUID objectiveId, EntityType<?> entityType) {
        super(objectiveId, entityType);
    }

    @Deprecated
    public ObjectiveEntity(UUID objectiveId, Item ignoredIcon, EntityType<?> entityType) {
        this(objectiveId, entityType);
    }

    public EntityType<?> getEntityType() {
        return getValue();
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<EntityType<?>, Integer> objectiveProgress) {
        // todo; called when the player kills an entity.
        // todo; perhaps we need some sort of context object
    }

    @Override
    public QuestObjectiveSerializer getType() {
        return QuestObjectiveTypes.ENTITY;
    }
}
