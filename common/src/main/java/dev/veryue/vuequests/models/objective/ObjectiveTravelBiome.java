package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveTravelBiome extends BooleanQuestObjective<Identifier> {
    public static final MapCodec<ObjectiveTravelBiome> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveTravelBiome::getObjectiveId),
            Identifier.CODEC.fieldOf("biome").forGetter(ObjectiveTravelBiome::getValue)
    ).apply(instance, ObjectiveTravelBiome::new));

    public ObjectiveTravelBiome(UUID objectiveId, Identifier biome) {
        super(objectiveId, biome);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.GRASS_BLOCK);
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.biome", getValue().toString());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<Identifier, Boolean> progress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player != null && context.getLevel().getBiome(player.blockPosition()).is(ResourceKey.create(Registries.BIOME, getValue()))) {
            progress.setCurrentProgress(true);
        }
    }

    @Override
    public QuestObjectiveSerializer<Boolean> getType() {
        return QuestObjectiveTypes.TRAVEL_BIOME;
    }
}
