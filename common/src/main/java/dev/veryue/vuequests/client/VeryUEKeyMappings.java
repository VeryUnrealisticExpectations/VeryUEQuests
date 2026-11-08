package dev.veryue.vuequests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.screens.QuestScreen;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class VeryUEKeyMappings {

    public static final KeyMapping OPEN_QUEST_MENU = new KeyMapping(
            "key." + MOD_ID + ".open_quest_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories." + MOD_ID
    );

    public static void onClientTick(Minecraft client, Consumer<CustomPacketPayload> sendToServer) {
        while (OPEN_QUEST_MENU.consumeClick()) {
            if (client.player != null && client.screen == null) {
                QuestScreen screen = new QuestScreen(sendToServer::accept);
                client.setScreen(screen);
                if (client.screen == screen) {
                    sendToServer.accept(QuestScreenOpenedPayload.INSTANCE);
                }
            }
        }
    }
}
