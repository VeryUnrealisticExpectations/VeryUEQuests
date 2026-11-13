package dev.veryue.vuequests.editor.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class QuestApiTextProgressObjective extends QuestObjective<String, String> {
    public static final MapCodec<QuestApiTextProgressObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(QuestApiTextProgressObjective::getObjectiveId),
            Codec.STRING.fieldOf("value").forGetter(QuestApiTextProgressObjective::getValue)
    ).apply(instance, QuestApiTextProgressObjective::new));
    public static final QuestObjectiveSerializer<String> TYPE = QuestObjectiveTypes.register(
            Identifier.parse("test:text_progress"), CODEC, Codec.STRING, null);

    public QuestApiTextProgressObjective(UUID id, String value) {
        super(id, value);
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<String, String> progress, ObjectiveContext context) {
    }

    @Override
    public boolean isComplete(QuestObjectiveProgress<String, String> progress) {
        return getValue().equals(progress.getCurrentProgress());
    }

    @Override
    protected MutableComponent formatProgress(@Nullable String progress, boolean complete) {
        return progress == null || complete ? super.formatProgress(progress, complete) : Component.literal(progress);
    }

    @Override
    public QuestObjectiveSerializer<String> getType() {
        return TYPE;
    }

    public static void init() {}
}
