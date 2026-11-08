package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.UUID;

public class ObjectiveSlayEntity extends CountedQuestObjective<EntityType<?>> {

    public static final MapCodec<ObjectiveSlayEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveSlayEntity::getObjectiveId),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(ObjectiveSlayEntity::getEntityType),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(objective -> Optional.of(objective.getCount()))
    ).apply(instance, (id, entityType, count) -> new ObjectiveSlayEntity(id, entityType, count.orElse(1))));

    public ObjectiveSlayEntity(UUID objectiveId, EntityType<?> entityType) {
        this(objectiveId, entityType, 1);
    }

    public ObjectiveSlayEntity(UUID objectiveId, EntityType<?> entityType, int count) {
        super(objectiveId, entityType, count);
    }

    @Deprecated
    public ObjectiveSlayEntity(UUID objectiveId, Item ignoredIcon, EntityType<?> entityType) {
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
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.slay", getCount(), getEntityType().getDescription());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<EntityType<?>, Integer> objectiveProgress, ObjectiveContext context) {
        Entity entity = context.getParamOrNull(ObjectiveContext.ENTITY);
        int currentProgress = getProgressCount(objectiveProgress.getCurrentProgress());
        if (entity != null && entity.getType() == getEntityType() && currentProgress < getCount()) {
            objectiveProgress.setCurrentProgress(currentProgress + 1);
        }
    }

    @Override
    public QuestObjectiveSerializer<Integer> getType() {
        return QuestObjectiveTypes.ENTITY;
    }
}
