package dev.veryue.vuequests.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface LinearQuestHeadingDecoration {

    void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height);
}
