package dev.veryue.vuequests.models.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record QuestDisplay(Component title, Component subtitle, Component description, Item icon, String tooltip,
                           Identifier backgroundSprite) {

    public static final String BACKGROUND_SPRITE_FOLDER = "quest_backgrounds";
    public static final Identifier DEFAULT_BACKGROUND_SPRITE =
            Identifier.fromNamespaceAndPath(MOD_ID, BACKGROUND_SPRITE_FOLDER + "/background_icon_square");

    public static final Codec<QuestDisplay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestDisplay::title),
            ComponentSerialization.CODEC.fieldOf("subtitle").forGetter(QuestDisplay::subtitle),
            ComponentSerialization.CODEC.fieldOf("description").forGetter(QuestDisplay::description),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(QuestDisplay::icon),
            Codec.STRING.fieldOf("tooltip").forGetter(QuestDisplay::tooltip),
            Identifier.CODEC.optionalFieldOf("background_sprite", DEFAULT_BACKGROUND_SPRITE).forGetter(QuestDisplay::backgroundSprite)
    ).apply(instance, QuestDisplay::new));

    public QuestDisplay(Component title, Component subtitle, Component description, Item icon, String tooltip) {
        this(title, subtitle, description, icon, tooltip, DEFAULT_BACKGROUND_SPRITE);
    }

    public QuestDisplay(String title, String subtitle, String description, Item icon, String tooltip) {
        this(Component.literal(title), Component.literal(subtitle), Component.literal(description), icon, tooltip);
    }
}
