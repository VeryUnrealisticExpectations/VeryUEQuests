package dev.veryue.vuequests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.screens.GraphQuestScreen;
import dev.veryue.vuequests.client.screens.LinearQuestScreen;
import dev.veryue.vuequests.client.screens.QuestMenuNavigation;
import dev.veryue.vuequests.client.screens.QuestMenuPage;
import dev.veryue.vuequests.client.screens.QuestSettingsScreen;
import dev.veryue.vuequests.config.QuestView;
import net.minecraft.client.gui.screens.Screen;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class VeryUEKeyMappings {

    public static final KeyMapping OPEN_QUEST_MENU = new KeyMapping(
            "key." + MOD_ID + ".open_quest_menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "quests"))
    );

    public static void onClientTick(Minecraft client, Consumer<CustomPacketPayload> sendToServer, QuestCanvasKeyMappings canvasKeys, QuestMenuKeyMappings menuKeys) {
        while (OPEN_QUEST_MENU.consumeClick()) {
            QuestView view = ClientQuestData.getQuestView();
            if (client.player != null && client.screen == null && view != null) {
                QuestMenuNavigation navigation = new QuestMenuNavigation(menuKeys, List.of(
                        new QuestMenuPage(Component.translatable("screen.vuequests.quests"), menu -> view == QuestView.LIST ? new LinearQuestScreen(sendToServer, menu) : new GraphQuestScreen(sendToServer, canvasKeys, menu)),
                        new QuestMenuPage(Component.translatable("screen.vuequests.settings"), menu -> new QuestSettingsScreen(menu, sendToServer))
                ));
                Screen screen = navigation.select(0);
                client.setScreen(screen);
                if (client.screen == screen) {
                    sendToServer.accept(QuestScreenOpenedPayload.INSTANCE);
                }
            }
        }
    }
}
