package dev.veryue.vuequests.client.widget;

import dev.veryue.vuequests.client.util.GuiSpriteRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class LinearQuestSprites {

    public static final Identifier BACKGROUND = QuestWidgetSprites.sprite("linear_background");
    public static final Identifier FRAME = QuestWidgetSprites.sprite("linear_frame");
    public static final Identifier COLUMN_DIVIDER = QuestWidgetSprites.sprite("linear_column_divider");
    public static final Identifier BREAKLINE = QuestWidgetSprites.sprite("linear_breakline");
    public static final int CORNER_RADIUS = 6;

    public static void background(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {

        int[] insets = {4, 2, 1, 1, 0, 0};
        for (int row = 0; row < CORNER_RADIUS; row++) {
            graphics.enableScissor(x + insets[row], y + row, x + width - insets[row], y + row + 1);
            GuiSpriteRenderer.blit(graphics, BACKGROUND, x, y, width, height, 0xFFFFFFFF);
            graphics.disableScissor();
        }
        graphics.enableScissor(x, y + CORNER_RADIUS, x + width, y + height);
        GuiSpriteRenderer.blit(graphics, BACKGROUND, x, y, width, height, 0xFFFFFFFF);
        graphics.disableScissor();
        GuiSpriteRenderer.blit(graphics, FRAME, x, y, width, height, 0xFFFFFFFF);
    }

    public static void heading(GuiGraphicsExtractor graphics, Font font, Component heading, int x, int y, int width,
                               boolean showDecoration, LinearQuestHeadingDecoration decoration) {
        int textWidth = font.width(heading);
        float textScale = Math.min(1, width / (float) Math.max(1, textWidth));
        int armWidth = Math.max(0, (width - textWidth) / 2 - 8);
        if (showDecoration && armWidth >= 10) {
            drawDecoration(graphics, decoration, x, y, armWidth);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + width, y);
            graphics.pose().scale(-1, 1);
            try {
                drawDecoration(graphics, decoration, 0, 0, armWidth);
            } finally {
                graphics.pose().popMatrix();
            }
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + width / 2F, y);
        graphics.pose().scale(textScale, textScale);
        graphics.text(font, heading, -textWidth / 2, 0, 0xFFE0E0E0);
        graphics.pose().popMatrix();
    }

    private static void drawDecoration(GuiGraphicsExtractor graphics, LinearQuestHeadingDecoration decoration, int x, int y, int width) {
        if (decoration == null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BREAKLINE, x, y, width, 9);
        } else {
            decoration.draw(graphics, x, y, width, 9);
        }
    }
}
