package dev.veryue.vuequests.client.screens;

import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPart;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPlacement;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.Map;

public record LinearQuestLayout(ScreenRectangle navigation, ScreenRectangle questList, ScreenRectangle questDetails,
                                boolean navigationChevrons, boolean compactRows, boolean clearWorld,
                                Map<LinearQuestDetailPart, LinearQuestDetailPlacement> detailParts) {

    public static final LinearQuestLayout DEFAULT = new LinearQuestLayout(null, null, null, false, false, false, Map.of());

    public boolean hasPlacements() {
        return navigation != null || questList != null || questDetails != null;
    }
}
