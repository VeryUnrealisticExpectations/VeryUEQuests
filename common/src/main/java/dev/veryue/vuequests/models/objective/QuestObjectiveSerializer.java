package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.editor.api.models.JsonSchema;

public record QuestObjectiveSerializer(MapCodec<? extends QuestObjective<?, ?>> codec, JsonSchema schema) {
    public QuestObjectiveSerializer(MapCodec<? extends QuestObjective<?, ?>> codec) { this(codec, null); }
}
