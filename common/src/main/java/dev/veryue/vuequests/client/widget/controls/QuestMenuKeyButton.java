package dev.veryue.vuequests.client.widget.controls;

import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class QuestMenuKeyButton extends Button {

    public static final int SIZE = 16;
    private static final Identifier KEYCAP = QuestWidgetSprites.sprite("keycap");

    private final Font font;
    private final Component keyLabel;

    public QuestMenuKeyButton(Font font, KeyMapping mapping, OnPress onPress) {
        super(0, 0, SIZE, SIZE, Component.translatable(mapping.getName()), onPress, DEFAULT_NARRATION);
        this.font = font;
        keyLabel = Component.keybind(mapping.getName());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int color = active && isHoveredOrFocused() ? 0xFFF0F0E8 : 0xFF9B9B95;
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
