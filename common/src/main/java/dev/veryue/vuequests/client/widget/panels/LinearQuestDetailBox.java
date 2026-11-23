package dev.veryue.vuequests.client.widget.panels;

import net.minecraft.client.gui.navigation.ScreenRectangle;

record LinearQuestDetailBox(ScreenRectangle bounds, boolean pinned, int contentHeight, int flowTop) {

    int maxScroll() {
        return pinned ? Math.max(0, contentHeight - bounds.height()) : 0;
    }
}
