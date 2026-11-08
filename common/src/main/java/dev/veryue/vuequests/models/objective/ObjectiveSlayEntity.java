package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveSlayEntity extends QuestObjective<EntityType<?>, Integer> {

    public static final MapCodec<ObjectiveSlayEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveSlayEntity::getObjectiveId),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(ObjectiveSlayEntity::getEntityType)
    ).apply(instance, ObjectiveSlayEntity::new));

    public ObjectiveSlayEntity(UUID objectiveId, EntityType<?> entityType) {
        super(objectiveId, entityType);
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
        return Component.translatable("screen.vuequests.objective.slay", getEntityType().getDescription());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<EntityType<?>, Integer> objectiveProgress, ObjectiveContext context) {
        // todo; called when the player kills an entity.
        // todo; perhaps we need some sort of context object
    }

    @Override
    public boolean isComplete(QuestObjectiveProgress<EntityType<?>, Integer> progress) {
        return progress.getCurrentProgress() != null && progress.getCurrentProgress() > 0;
    }

    @Override
    public QuestObjectiveSerializer<Integer> getType() {
        return QuestObjectiveTypes.ENTITY;
    }
}
