package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveGetAdvancement extends BooleanQuestObjective<ResourceLocation> {

    public static final MapCodec<ObjectiveGetAdvancement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveGetAdvancement::getObjectiveId),
            ResourceLocation.CODEC.fieldOf("advancement").forGetter(ObjectiveGetAdvancement::getValue)
    ).apply(instance, ObjectiveGetAdvancement::new));

    public ObjectiveGetAdvancement(UUID objectiveId, ResourceLocation advancement) {
        super(objectiveId, advancement);
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<ResourceLocation, Boolean> objectiveProgress, ObjectiveContext context) {
        ResourceLocation earned = context.getParamOrNull(ObjectiveContext.ADVANCEMENT);
        if (earned != null) {
            if (earned.equals(getValue())) {
                objectiveProgress.setCurrentProgress(true);
            }
            return;
        }

        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        if (player != null) {
            AdvancementHolder advancement = player.server.getAdvancements().get(getValue());
            if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                objectiveProgress.setCurrentProgress(true);
            }
        }
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.advancement", getValue().toString());
    }

    @Override
    public QuestObjectiveSerializer<Boolean> getType() {
        return QuestObjectiveTypes.ADVANCEMENT;
    }
}
