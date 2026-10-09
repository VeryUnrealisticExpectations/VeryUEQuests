package dev.veryue.vuequests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.screens.QuestScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class VeryUEKeyMappings {

    public static final KeyMapping OPEN_QUEST_MENU = new KeyMapping(
            "key." + MOD_ID + ".open_quest_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories." + MOD_ID
    );

    public static void onClientTick(Minecraft client) {
        while (OPEN_QUEST_MENU.consumeClick()) {
            if (client.player != null && client.screen == null) {
                client.setScreen(new QuestScreen());
            }
        }
    }
}
