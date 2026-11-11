package dev.veryue.vuequests.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum QuestView implements StringRepresentable {
    GRAPH("graph"),
    LIST("list");

    public static final Codec<QuestView> CODEC = StringRepresentable.fromEnum(QuestView::values);
    private final String name;

    QuestView(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
