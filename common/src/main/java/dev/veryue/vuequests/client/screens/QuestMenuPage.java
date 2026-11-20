package dev.veryue.vuequests.client.screens;

import net.minecraft.network.chat.Component;

import java.util.function.Function;

public class QuestMenuPage {

    private final Component title;
    private final Function<QuestMenuNavigation, QuestMenuScreen> screenFactory;
    private QuestMenuScreen screen;

    public QuestMenuPage(Component title, Function<QuestMenuNavigation, QuestMenuScreen> screenFactory) {
        this.title = title;
        this.screenFactory = screenFactory;
    }

    public Component getTitle() {
        return title;
    }

    public QuestMenuScreen getScreen(QuestMenuNavigation navigation) {
        if (screen == null) {
            screen = screenFactory.apply(navigation);
        }
        return screen;
    }
}
