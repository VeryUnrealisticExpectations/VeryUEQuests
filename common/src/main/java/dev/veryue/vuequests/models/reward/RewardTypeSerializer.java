package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.editor.api.models.JsonSchema;

public record RewardTypeSerializer(MapCodec<? extends RewardType<?>> codec, JsonSchema schema) {
    public RewardTypeSerializer(MapCodec<? extends RewardType<?>> codec) { this(codec, null); }
}
