package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.UUID;

public class ObjectiveTravelStructure extends BooleanQuestObjective<Identifier> {
    public static final MapCodec<ObjectiveTravelStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveTravelStructure::getObjectiveId),
            Identifier.CODEC.fieldOf("structure").forGetter(ObjectiveTravelStructure::getValue)
    ).apply(instance, ObjectiveTravelStructure::new));

    public ObjectiveTravelStructure(UUID objectiveId, Identifier structure) {
        super(objectiveId, structure);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.STONE_BRICKS);
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.structure", getValue().toString());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<Identifier, Boolean> progress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player == null) {
            return;
        }
        Holder.Reference<Structure> structure = context.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, getValue())).orElse(null);
        if (structure != null && context.getLevel().structureManager().getStructureWithPieceAt(player.blockPosition(), candidate -> candidate.equals(structure)).isValid()) {
            progress.setCurrentProgress(true);
        }
    }

    @Override
    public QuestObjectiveSerializer<Boolean> getType() {
        return QuestObjectiveTypes.TRAVEL_STRUCTURE;
    }
}
