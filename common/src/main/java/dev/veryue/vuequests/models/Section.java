package dev.veryue.vuequests.models;

import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;

public class Section {


    private final UUID sectionUUID;


    // todo; let this be colorable;
    private final String name;
    private final Item icon;

    private final List<Quest> questList;

    public Section(UUID sectionUUID, String name, Item icon, List<Quest> questList) {
        this.sectionUUID = sectionUUID;
        this.name = name;
        this.icon = icon;
        this.questList = questList;
    }
}
