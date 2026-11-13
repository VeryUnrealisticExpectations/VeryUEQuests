package dev.veryue.vuequests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.screens.QuestScreen;
import dev.veryue.vuequests.client.screens.LinearQuestScreen;
import dev.veryue.vuequests.config.QuestView;
import net.minecraft.client.gui.screens.Screen;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class VeryUEKeyMappings {

    public static final KeyMapping OPEN_QUEST_MENU = new KeyMapping(
            "key." + MOD_ID + ".open_quest_menu",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_J,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "quests"))
    );

    public static void onClientTick(Minecraft client, Consumer<CustomPacketPayload> sendToServer,
                                    QuestCanvasKeyMappings canvasKeys) {
        while (OPEN_QUEST_MENU.consumeClick()) {
            QuestView view = ClientQuestData.getQuestView();
            if (client.player != null && client.gui.screen() == null && view != null) {
                Screen screen = view == QuestView.LIST ? new LinearQuestScreen(sendToServer)
                        : new QuestScreen(sendToServer, canvasKeys);
                client.gui.setScreen(screen);
                if (client.gui.screen() == screen) {
                    sendToServer.accept(QuestScreenOpenedPayload.INSTANCE);
                }
            }
        }
    }
}
