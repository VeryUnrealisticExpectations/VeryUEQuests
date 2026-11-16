package dev.veryue.vuequests.client.screens;

import dev.veryue.vuequests.client.QuestMenuKeyMappings;

import java.util.List;

public class QuestMenuNavigation {

    private final QuestMenuKeyMappings keyMappings;
    private final List<QuestMenuPage> pages;
    private int selectedIndex;

    public QuestMenuNavigation(QuestMenuKeyMappings keyMappings, List<QuestMenuPage> pages) {
        if (pages.isEmpty()) throw new IllegalArgumentException("A quest menu must contain at least one page");
        this.keyMappings = keyMappings;
        this.pages = pages;
    }

    public QuestMenuKeyMappings getKeyMappings() {
        return keyMappings;
    }

    public List<QuestMenuPage> getPages() {
        return pages;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public QuestMenuScreen select(int index) {
        selectedIndex = Math.floorMod(index, pages.size());
        return pages.get(selectedIndex).getScreen(this);
    }

    public QuestMenuScreen getLayoutScreen() {
        return pages.getFirst().getScreen(this);
    }
}
