package dev.veryue.vuequests.client.widget.panels;

import dev.veryue.vuequests.client.widget.LinearQuestHeadingDecoration;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public record LinearQuestDetailPlacement(ScreenRectangle bounds, LinearQuestDetailPart follow, int gap,
                                         LinearQuestTextAlignment alignment, float textScale, int spacing,
                                         boolean showHeader, boolean showDecoration, Component label,
                                         LinearQuestHeadingDecoration decoration) {
}
