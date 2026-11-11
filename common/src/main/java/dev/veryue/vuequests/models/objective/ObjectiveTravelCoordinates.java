package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveTravelCoordinates extends BooleanQuestObjective<ObjectiveArea> {
    public static final MapCodec<ObjectiveTravelCoordinates> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveTravelCoordinates::getObjectiveId),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(ObjectiveTravelCoordinates::getDimension),
            ObjectiveArea.CODEC.forGetter(ObjectiveTravelCoordinates::getValue)
    ).apply(instance, ObjectiveTravelCoordinates::new));

    private final ResourceLocation dimension;

    public ObjectiveTravelCoordinates(UUID objectiveId, ResourceLocation dimension, ObjectiveArea area) {
        super(objectiveId, area);
        this.dimension = dimension;
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.COMPASS);
    }

    @Override
    public Component getActionText() {
        ObjectiveArea area = getValue();
        return Component.translatable("screen.vuequests.objective.coordinates", dimension.toString(),
                area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<ObjectiveArea, Boolean> progress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player != null && dimension.equals(context.getLevel().dimension().location()) && getValue().contains(player.blockPosition())) {
            progress.setCurrentProgress(true);
        }
    }

    @Override
    public QuestObjectiveSerializer<Boolean> getType() {
        return QuestObjectiveTypes.TRAVEL_COORDINATES;
    }
}
