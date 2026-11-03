package dev.veryue.vuequests.editor.api;

import dev.veryue.vuequests.editor.api.models.JsonSchema;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public class QuestApiTestObjective extends QuestObjective<Integer> {
    public static final MapCodec<QuestApiTestObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(QuestApiTestObjective::getObjectiveId),
            Codec.INT.fieldOf("custom_value").forGetter(QuestApiTestObjective::getValue)
    ).apply(instance, QuestApiTestObjective::new));
    public static final QuestObjectiveSerializer TYPE = QuestObjectiveTypes.register(ResourceLocation.parse("test:custom"), CODEC,
            QuestApiSchemas.objective("test:custom", "custom_value", JsonSchema.type("integer")));
    public static final QuestObjectiveSerializer LEGACY = QuestObjectiveTypes.register(ResourceLocation.parse("test:legacy"), CODEC);

    public QuestApiTestObjective(UUID id, int value) { super(id, value); }
    @Override public QuestObjectiveSerializer getType() { return TYPE; }
}
