package dev.veryue.vuequests.client.widget.controls;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class QuestMenuTabButton extends Button {

    private static final int PADDING = 7;

    private final Font font;
    private final BooleanSupplier selected;

    public QuestMenuTabButton(Font font, Component title, BooleanSupplier selected, OnPress onPress) {
        super(0, 0, 0, 24, title, onPress, DEFAULT_NARRATION);
        this.font = font;
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = selected.getAsBoolean();
        int color = isSelected || isHoveredOrFocused() ? 0xFFF0F0E8 : 0xFF9B9B95;
        int maxWidth = Math.max(1, getWidth() - 2 * PADDING);
        int textWidth = font.width(getMessage());
        float scale = Math.min(1, maxWidth / (float) Math.max(1, textWidth));
        float centerX = getX() + getWidth() / 2F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX,
                getY() + (getHeight() - font.lineHeight) / 2 + (1 - scale) * font.lineHeight / 2F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, getMessage(), -textWidth / 2, 0, color);
        graphics.pose().popMatrix();
        if (isSelected) {
            int underlineWidth = Math.min(maxWidth, textWidth);
            int underlineX = (int) (centerX - underlineWidth / 2F);
            graphics.fill(underlineX, getBottom() - 4, underlineX + underlineWidth, getBottom() - 3, color);
        }
    }
}
