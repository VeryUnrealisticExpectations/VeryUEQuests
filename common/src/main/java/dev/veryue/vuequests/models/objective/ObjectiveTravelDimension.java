package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveTravelDimension extends BooleanQuestObjective<Identifier> {
    public static final MapCodec<ObjectiveTravelDimension> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveTravelDimension::getObjectiveId),
            Identifier.CODEC.fieldOf("dimension").forGetter(ObjectiveTravelDimension::getValue)
    ).apply(instance, ObjectiveTravelDimension::new));

    public ObjectiveTravelDimension(UUID objectiveId, Identifier dimension) {
        super(objectiveId, dimension);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.OBSIDIAN);
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.dimension", getValue().toString());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<Identifier, Boolean> progress, ObjectiveContext context) {
        if (context.getLevel().dimension().identifier().equals(getValue())) {
            progress.setCurrentProgress(true);
        }
    }

    @Override
    public QuestObjectiveSerializer<Boolean> getType() {
        return QuestObjectiveTypes.TRAVEL_DIMENSION;
    }
}
