package dev.veryue.vuequests.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    protected void extractTooltipForNextRenderPass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (isHovered()) {
            graphics.setTooltipForNextFrame(font, getMessage(), mouseX, mouseY);
        } else {
            super.extractTooltipForNextRenderPass(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.nextStratum();
        QuestWidgetSprites.blit(graphics, QuestWidgetSprites.SECTION_PANEL, getX(), getY(), getWidth(), getHeight(),
                isHoveredOrFocused() ? 1.2F : 1.0F);
        graphics.centeredText(font, collapsed ? ">" : "<", getX() + getWidth() / 2,
                getY() + (getHeight() - font.lineHeight) / 2, 0xFFE0E0E0);
    }
}
