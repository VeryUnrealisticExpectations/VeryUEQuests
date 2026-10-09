package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.UUID;

public class ObjectiveEntity extends QuestObjective<EntityType<?>> {

    public static final MapCodec<ObjectiveEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveEntity::getObjectiveId),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(ObjectiveEntity::getIcon),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(ObjectiveEntity::getEntityType)
    ).apply(instance, ObjectiveEntity::new));

    public ObjectiveEntity(UUID objectiveId, Item icon, EntityType<?> entityType) {
        super(objectiveId, icon, entityType);
    }

    public EntityType<?> getEntityType() {
        return getValue();
    }

    @Override
    public QuestObjectiveSerializer getType() {
        return QuestObjectiveTypes.ENTITY;
    }
}
