package dev.veryue.vuequests.models.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record QuestPosition(int x, int y) {

    public static final Codec<QuestPosition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(QuestPosition::x),
            Codec.INT.fieldOf("y").forGetter(QuestPosition::y)
    ).apply(instance, QuestPosition::new));
}
