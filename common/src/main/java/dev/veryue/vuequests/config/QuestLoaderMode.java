package dev.veryue.vuequests.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum QuestLoaderMode implements StringRepresentable {
    DATAPACK_BASED,
    CONFIG_BASED;

    public static final Codec<QuestLoaderMode> CODEC = StringRepresentable.fromEnum(QuestLoaderMode::values);

    @Override
    public String getSerializedName() {
        return name();
    }
}
