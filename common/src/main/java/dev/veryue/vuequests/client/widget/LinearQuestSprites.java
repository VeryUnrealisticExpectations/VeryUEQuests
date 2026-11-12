package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.veryue.vuequests.client.util.GuiSpriteRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class LinearQuestSprites {

    public static final ResourceLocation BACKGROUND = QuestWidgetSprites.sprite("linear_background");
    public static final ResourceLocation FRAME = QuestWidgetSprites.sprite("linear_frame");
    public static final ResourceLocation COLUMN_DIVIDER = QuestWidgetSprites.sprite("linear_column_divider");
    public static final ResourceLocation BREAKLINE = QuestWidgetSprites.sprite("linear_breakline");
    public static final int CORNER_RADIUS = 6;

    public static void background(GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
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

    public static void heading(GuiGraphics graphics, Font font, Component heading, int x, int y, int width) {
        int textWidth = font.width(heading);
        float textScale = Math.min(1, width / (float) Math.max(1, textWidth));
        int armWidth = Math.max(0, (width - textWidth) / 2 - 8);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (armWidth >= 10) {
            graphics.blitSprite(BREAKLINE, x, y, armWidth, 9);
            graphics.pose().pushPose();
            graphics.pose().translate(x + width, y, 0);
            graphics.pose().scale(-1, 1, 1);
            boolean culling = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            RenderSystem.disableCull();
            try {
                graphics.blitSprite(BREAKLINE, 0, 0, armWidth, 9);
            } finally {
                if (culling) RenderSystem.enableCull();
                graphics.pose().popPose();
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2F, y, 0);
        graphics.pose().scale(textScale, textScale, 1);
        graphics.drawString(font, heading, -textWidth / 2, 0, 0xFFE0E0E0);
        graphics.pose().popPose();
    }
}
