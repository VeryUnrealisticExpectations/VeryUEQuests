package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.locale.Language;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestWidgetSprites {

    public static final int HEADER_HEIGHT = 17;
    public static final float TEXTURE_ALPHA = 0.6F;
    public static final ResourceLocation SECTION_PANEL = sprite("section_panel");
    public static final ResourceLocation CANVAS = sprite("canvas");
    public static final ResourceLocation PREVIEW = sprite("preview");
    public static final ResourceLocation SECTION = sprite("section");
    public static final ResourceLocation SECTION_SELECTED = sprite("section_selected");
    public static final ResourceLocation SCROLLBAR = sprite("scrollbar");

    public static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_menu/" + name);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        blit(graphics, sprite, x, y, width, height, 1);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height, float brightness) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(brightness, brightness, brightness, TEXTURE_ALPHA);
        try {
            graphics.blitSprite(sprite, x, y, width, height);
        } finally {
            graphics.setColor(1, 1, 1, 1);
        }
    }

    public static void renderPanel(GuiGraphics graphics, Font font, AbstractWidget widget, ResourceLocation sprite) {
        blit(graphics, sprite, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(widget.getMessage(), Math.max(1, widget.getWidth() - 8))),
                widget.getX() + 4, widget.getY() + 4, 0xFFE0E0E0);
    }
}
