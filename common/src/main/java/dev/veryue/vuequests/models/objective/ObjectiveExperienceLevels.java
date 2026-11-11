package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveExperienceLevels extends CountedQuestObjective<Integer> {
    public static final MapCodec<ObjectiveExperienceLevels> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveExperienceLevels::getObjectiveId),
            ExtraCodecs.POSITIVE_INT.fieldOf("levels").forGetter(ObjectiveExperienceLevels::getCount)
    ).apply(instance, ObjectiveExperienceLevels::new));

    public ObjectiveExperienceLevels(UUID objectiveId, int levels) {
        super(objectiveId, levels, levels);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.levels", getCount());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<Integer, Integer> progress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player != null) {
            progress.setCurrentProgress(Math.max(getProgressCount(progress.getCurrentProgress()), Math.min(getCount(), player.experienceLevel)));
        }
    }

    @Override
    public QuestObjectiveSerializer<Integer> getType() {
        return QuestObjectiveTypes.EXPERIENCE_LEVELS;
    }
}
