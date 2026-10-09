package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.MapCodec;

public record RewardTypeSerializer(MapCodec<? extends RewardType<?>> codec) {
}
