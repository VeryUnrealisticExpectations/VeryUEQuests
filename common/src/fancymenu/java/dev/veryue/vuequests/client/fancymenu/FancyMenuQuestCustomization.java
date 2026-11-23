package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.customization.ScreenCustomization;
import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.customization.element.ElementRegistry;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayer;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import dev.veryue.vuequests.client.screens.LinearQuestCustomization;
import dev.veryue.vuequests.client.screens.LinearQuestLayout;
import dev.veryue.vuequests.client.screens.LinearQuestPanel;
import dev.veryue.vuequests.client.screens.LinearQuestScreen;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPart;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPlacement;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FancyMenuQuestCustomization extends LinearQuestCustomization {

    public FancyMenuQuestCustomization() {
        ElementRegistry.register(new QuestPanelElementBuilder("quest_navigation", LinearQuestPanel.NAVIGATION, 240, 24));
        ElementRegistry.register(new QuestPanelElementBuilder("quest_list", LinearQuestPanel.QUEST_LIST, 128, 160));
        ElementRegistry.register(new QuestPanelElementBuilder("quest_details", LinearQuestPanel.QUEST_DETAILS, 192, 160));
        ElementRegistry.register(new QuestPanelElementBuilder("quest_backdrop", LinearQuestPanel.BACKDROP, 128, 24));
        ElementRegistry.register(new QuestDetailElementBuilder(LinearQuestDetailPart.TITLE, 4, 24));
        ElementRegistry.register(new QuestDetailElementBuilder(LinearQuestDetailPart.DESCRIPTION, 36, 44));
        ElementRegistry.register(new QuestDetailElementBuilder(LinearQuestDetailPart.OBJECTIVES_HEADER, 84, 12));
        ElementRegistry.register(new QuestDetailElementBuilder(LinearQuestDetailPart.OBJECTIVES_LIST, 102, 44));
        ElementRegistry.register(new QuestDetailElementBuilder(LinearQuestDetailPart.REWARDS_HEADER, 150, 12));
        ElementRegistry.register(new QuestDetailElementBuilder(LinearQuestDetailPart.REWARDS_LIST, 168, 32));
    }

    @Override
    protected LinearQuestLayout resolveLayout(LinearQuestScreen screen) {
        if (!ScreenCustomization.isCustomizationEnabledForScreen(screen)) {
            return LinearQuestLayout.DEFAULT;
        }
        ScreenCustomizationLayer layer = ScreenCustomizationLayerHandler.getLayerOfScreen(screen);
        if (layer == null) {
            return LinearQuestLayout.DEFAULT;
        }
        EnumMap<LinearQuestPanel, ScreenRectangle> boundsByPanel = new EnumMap<>(LinearQuestPanel.class);
        boolean chevrons = false;
        boolean compactRows = false;
        QuestPanelElement details = null;
        List<AbstractElement> elements = layer.allElements;
        for (int index = elements.size() - 1; index >= 0; index--) {
            if (elements.get(index) instanceof QuestPanelElement element
                    && !boundsByPanel.containsKey(element.panel()) && element.shouldRender()) {
                ScreenRectangle bounds = element.bounds();
                if (screen.isUsablePlacement(element.panel(), bounds)) {
                    boundsByPanel.put(element.panel(), bounds);
                    if (element.panel() == LinearQuestPanel.NAVIGATION) {
                        chevrons = element.isStyleEnabled();
                    } else if (element.panel() == LinearQuestPanel.QUEST_LIST) {
                        compactRows = element.isStyleEnabled();
                    } else if (element.panel() == LinearQuestPanel.QUEST_DETAILS) {
                        details = element;
                    }
                }
            }
        }
        if (boundsByPanel.isEmpty()) {
            return LinearQuestLayout.DEFAULT;
        }
        return new LinearQuestLayout(boundsByPanel.get(LinearQuestPanel.NAVIGATION),
                boundsByPanel.get(LinearQuestPanel.QUEST_LIST), boundsByPanel.get(LinearQuestPanel.QUEST_DETAILS),
                chevrons, compactRows, boundsByPanel.containsKey(LinearQuestPanel.BACKDROP), resolveDetails(elements, details));
    }

    private Map<LinearQuestDetailPart, LinearQuestDetailPlacement> resolveDetails(List<AbstractElement> elements, QuestPanelElement parent) {
        if (parent == null) {
            return Map.of();
        }
        Map<LinearQuestDetailPart, LinearQuestDetailPlacement> parts = new EnumMap<>(LinearQuestDetailPart.class);
        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i) instanceof QuestDetailElement element && element.getParentLayout() == parent.getParentLayout()
                    && !parts.containsKey(element.part()) && element.shouldRender()) {
                parts.put(element.part(), element.placement(parent));
            }
        }
        return parts;
    }
}
