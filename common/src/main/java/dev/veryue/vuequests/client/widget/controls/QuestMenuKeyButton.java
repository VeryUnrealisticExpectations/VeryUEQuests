package dev.veryue.vuequests.client.widget.controls;

import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class QuestMenuKeyButton extends Button {

    public static final int SIZE = 16;
    private static final Identifier KEYCAP = QuestWidgetSprites.sprite("keycap");

    private final Font font;
    private final Component keyLabel;
    private final boolean previous;
    private boolean chevron;

    public QuestMenuKeyButton(Font font, KeyMapping mapping, boolean previous, OnPress onPress) {
        super(0, 0, SIZE, SIZE, Component.translatable(mapping.getName()), onPress, DEFAULT_NARRATION);
        this.font = font;
        keyLabel = Component.keybind(mapping.getName());
        this.previous = previous;
    }

    public void setChevron(boolean chevron) {
        if (this.chevron != chevron) {
            this.chevron = chevron;
            setTooltip(chevron ? Tooltip.create(Component.translatable("screen.vuequests.navigation.shortcut", getMessage(), keyLabel)) : null);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int color = active && isHoveredOrFocused() ? 0xFFF0F0E8 : 0xFF9B9B95;
        if (chevron) {
            int centerY = getY() + SIZE / 2;
            int direction = previous ? 1 : -1;
            int tipX = getX() + SIZE / 2 - 2 * direction;
            for (int step = 0; step <= 4; step++) {
                int x = tipX + direction * step;
                graphics.fill(x, centerY - step, x + 1, centerY - step + 2, color);
                graphics.fill(x, centerY + step, x + 1, centerY + step + 2, color);
            }
            return;
        }
        QuestWidgetSprites.blit(graphics, KEYCAP, getX(), getY(), SIZE, SIZE,
                active && isHoveredOrFocused() ? 1.25F : 1);
        int textWidth = font.width(keyLabel);
        float scale = Math.min(1, (SIZE - 4) / (float) Math.max(1, textWidth));
        graphics.pose().pushMatrix();
        graphics.pose().translate(getX() + SIZE / 2F, getY() + 4 + (1 - scale) * font.lineHeight / 2F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, keyLabel, -textWidth / 2, 0, color);
        graphics.pose().popMatrix();
    }
}
