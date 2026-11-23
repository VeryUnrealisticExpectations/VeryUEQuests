package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.util.properties.Property;
import de.keksuccino.fancymenu.util.properties.Property.BooleanProperty;
import dev.veryue.vuequests.client.screens.LinearQuestPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public class QuestPanelElement extends AbstractElement {

    private final LinearQuestPanel panel;
    final BooleanProperty style;

    public QuestPanelElement(QuestPanelElementBuilder builder, LinearQuestPanel panel, int width, int height) {
        super(builder);
        this.panel = panel;
        baseWidth = width;
        baseHeight = height;
        style = switch (panel) {
            case NAVIGATION -> putProperty(Property.booleanProperty("vuequests_navigation_chevrons", false,
                    "fancymenu.vuequests.navigation.chevrons"));
            case QUEST_LIST -> putProperty(Property.booleanProperty("vuequests_compact_rows", false,
                    "fancymenu.vuequests.list.compact_rows"));
            case QUEST_DETAILS, BACKDROP -> null;
        };
        setSupportsRotation(false);
        setSupportsTilting(false);
    }

    public LinearQuestPanel panel() {
        return panel;
    }

    public ScreenRectangle bounds() {
        return new ScreenRectangle(getAbsoluteX(), getAbsoluteY(), getAbsoluteWidth(), getAbsoluteHeight());
    }

    public boolean isStyleEnabled() {
        return style != null && style.tryGetNonNull();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isEditor()) {
            return;
        }
        renderMarker(graphics, bounds(), getDisplayName());
    }

    static void renderMarker(GuiGraphics graphics, ScreenRectangle bounds, Component label) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0x80222C38);
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        try {
            graphics.drawCenteredString(font, label, bounds.left() + bounds.width() / 2,
                    bounds.top() + (bounds.height() - font.lineHeight) / 2, 0xFFFFFFFF);
        } finally {
            graphics.disableScissor();
        }
    }
}
