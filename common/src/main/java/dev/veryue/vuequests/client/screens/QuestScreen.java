package dev.veryue.vuequests.client.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestScreen extends Screen {

    public QuestScreen() {
        super(Component.translatable("screen." + MOD_ID + ".quest_menu"));
    }
}
