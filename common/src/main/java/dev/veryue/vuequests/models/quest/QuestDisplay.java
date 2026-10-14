package dev.veryue.vuequests.models.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.Item;

public record QuestDisplay(Component title, Component subtitle, Component description, Item icon, String tooltip) {

    public static final Codec<QuestDisplay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestDisplay::title),
            ComponentSerialization.CODEC.fieldOf("subtitle").forGetter(QuestDisplay::subtitle),
            ComponentSerialization.CODEC.fieldOf("description").forGetter(QuestDisplay::description),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(QuestDisplay::icon),
            Codec.STRING.fieldOf("tooltip").forGetter(QuestDisplay::tooltip)
    ).apply(instance, QuestDisplay::new));

    public QuestDisplay(String title, String subtitle, String description, Item icon, String tooltip) {
        this(Component.literal(title), Component.literal(subtitle), Component.literal(description), icon, tooltip);
    }
}
