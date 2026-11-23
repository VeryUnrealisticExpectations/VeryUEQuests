package dev.veryue.vuequests.client.widget;

import net.minecraft.client.gui.GuiGraphics;

@FunctionalInterface
public interface LinearQuestHeadingDecoration {

    void draw(GuiGraphics graphics, int x, int y, int width, int height);
}
