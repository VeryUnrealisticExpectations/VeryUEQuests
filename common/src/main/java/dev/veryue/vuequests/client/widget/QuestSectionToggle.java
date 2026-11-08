package dev.veryue.vuequests.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class QuestSectionToggle extends Button {

    private final Font font;
    private boolean collapsed;

    public QuestSectionToggle(Font font, OnPress onPress) {
        super(0, 0, 12, 0, Component.empty(), onPress, DEFAULT_NARRATION);
        this.font = font;
        setCollapsed(false);
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        setMessage(Component.translatable(collapsed ? "screen.vuequests.expand_sections" : "screen.vuequests.collapse_sections"));
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 180);
        QuestWidgetSprites.blit(graphics, QuestWidgetSprites.SECTION_PANEL, getX(), getY(), getWidth(), getHeight(),
                isHoveredOrFocused() ? 1.2F : 1.0F);
        graphics.drawCenteredString(font, collapsed ? ">" : "<", getX() + getWidth() / 2,
                getY() + (getHeight() - font.lineHeight) / 2, 0xFFE0E0E0);
        graphics.pose().popPose();
    }
}
