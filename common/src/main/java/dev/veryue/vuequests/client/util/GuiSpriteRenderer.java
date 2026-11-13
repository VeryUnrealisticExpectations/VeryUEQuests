package dev.veryue.vuequests.client.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class GuiSpriteRenderer {

    public static void blit(GuiGraphicsExtractor graphics, Identifier location, int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, location, x, y, width, height, color);
        }
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier location, int x, int y, int width, int height,
                            float red, float green, float blue, float alpha) {
        blit(graphics, location, x, y, width, height, ARGB.color(channel(alpha), channel(red), channel(green), channel(blue)));
    }

    private static int channel(float value) {
        return Mth.clamp(Math.round(value * 255), 0, 255);
    }
}
