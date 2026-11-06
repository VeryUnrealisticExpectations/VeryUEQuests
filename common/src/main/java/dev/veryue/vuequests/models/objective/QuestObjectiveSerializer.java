package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.editor.api.models.JsonSchema;

public record QuestObjectiveSerializer<P>(MapCodec<? extends QuestObjective<?, P>> codec, Codec<P> progressCodec, JsonSchema schema) {
    public QuestObjectiveSerializer(MapCodec<? extends QuestObjective<?, P>> codec, JsonSchema schema) {
        this(codec, Codec.PASSTHROUGH.flatXmap(
                value -> DataResult.error(() -> "This objective type must register a progress codec to deserialize progress."),
                value -> DataResult.error(() -> "This objective type must register a progress codec to serialize progress.")), schema);
    }

    public QuestObjectiveSerializer(MapCodec<? extends QuestObjective<?, P>> codec) {
        this(codec, null);
    }
}
