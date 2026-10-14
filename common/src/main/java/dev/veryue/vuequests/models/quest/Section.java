package dev.veryue.vuequests.models.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;

public record Section(UUID sectionUUID, Component name, Item icon, boolean disabled, String modLoaded,
                      List<Quest> questList) {

    public static final MapCodec<String> MOD_LOADED_CODEC = Codec.STRING.optionalFieldOf("modLoaded", "");

    public static final Codec<Section> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("section_id").forGetter(Section::sectionUUID),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(Section::name),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(Section::icon),
            Codec.BOOL.fieldOf("disabled").forGetter(Section::disabled),
            MOD_LOADED_CODEC.forGetter(Section::modLoaded),
            Quest.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(Section::questList)
    ).apply(instance, Section::new));


    public Section(UUID sectionUUID, Component name, Item icon, List<Quest> questList) {
        this(sectionUUID, name, icon, false, questList);
    }

    public Section(UUID sectionUUID, Component name, Item icon, boolean disabled, List<Quest> questList) {
        this(sectionUUID, name, icon, disabled, "", questList);
    }

    public Section(UUID sectionUUID, String name, Item icon, List<Quest> questList) {
        this(sectionUUID, Component.literal(name), icon, false, "", questList);
    }

    public Section(UUID sectionUUID, String name, Item icon, boolean disabled, List<Quest> questList) {
        this(sectionUUID, Component.literal(name), icon, disabled, "", questList);
    }

    public Section(UUID sectionUUID, String name, Item icon, boolean disabled, String modLoaded, List<Quest> questList) {
        this(sectionUUID, Component.literal(name), icon, disabled, modLoaded, questList);
    }

}
