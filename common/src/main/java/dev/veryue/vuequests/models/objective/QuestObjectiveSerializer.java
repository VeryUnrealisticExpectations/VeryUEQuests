package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;

public record QuestObjectiveSerializer(MapCodec<? extends QuestObjective<?>> codec) { }
