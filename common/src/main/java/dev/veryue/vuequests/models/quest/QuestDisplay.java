package dev.veryue.vuequests.models.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public record QuestDisplay(String title, String subtitle, String description, Item icon, String tooltip) {

    public static final Codec<QuestDisplay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("title").forGetter(QuestDisplay::title),
            Codec.STRING.fieldOf("subtitle").forGetter(QuestDisplay::subtitle),
            Codec.STRING.fieldOf("description").forGetter(QuestDisplay::description),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(QuestDisplay::icon),
            Codec.STRING.fieldOf("tooltip").forGetter(QuestDisplay::tooltip)
    ).apply(instance, QuestDisplay::new));
}
