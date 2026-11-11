package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class ObjectiveAcknowledge extends BooleanQuestObjective<Boolean> {
    public static final MapCodec<ObjectiveAcknowledge> CODEC = UUIDUtil.STRING_CODEC.fieldOf("objective_id")
            .xmap(ObjectiveAcknowledge::new, ObjectiveAcknowledge::getObjectiveId);

    public ObjectiveAcknowledge(UUID objectiveId) {
        super(objectiveId, true);
    }

    @Override
    public boolean requiresInteraction() {
        return true;
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.acknowledge");
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<Boolean, Boolean> progress, ObjectiveContext context) {
        if (context.getParamOrNull(ObjectiveContext.PLAYER) != null && getObjectiveId().equals(context.getParamOrNull(ObjectiveContext.INTERACTION))) {
            progress.setCurrentProgress(true);
        }
    }

    @Override
    public QuestObjectiveSerializer<Boolean> getType() {
        return QuestObjectiveTypes.ACKNOWLEDGEMENT;
    }
}
