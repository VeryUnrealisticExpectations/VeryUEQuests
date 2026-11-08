package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.locale.Language;
import net.minecraft.util.FastColor;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestWidgetSprites {

    public static final int HEADER_HEIGHT = 17;
    public static final float TEXTURE_ALPHA = 0.6F;
    public static final ResourceLocation SECTION_PANEL = sprite("section_panel");
    public static final ResourceLocation CANVAS = sprite("canvas");
    public static final ResourceLocation DETAILS_PANEL = sprite("details_panel");
    public static final ResourceLocation CLOSE = sprite("close");
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
        blit(graphics, sprite, x, y, width, height, brightness, brightness, brightness);
    }

    public static void blitTinted(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height, int tint) {
        blit(graphics, sprite, x, y, width, height,
                FastColor.ARGB32.red(tint) / 255F, FastColor.ARGB32.green(tint) / 255F, FastColor.ARGB32.blue(tint) / 255F);
    }

    private static void blit(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height,
                             float red, float green, float blue) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(red, green, blue, TEXTURE_ALPHA);
        try {
            graphics.blitSprite(sprite, x, y, width, height);
        } finally {
            graphics.setColor(1, 1, 1, 1);
        }
    }

    public static void renderHeader(GuiGraphics graphics, Font font, Component title, int x, int y, int width) {
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(title, Math.max(1, width - 8))),
                x + 4, y + 4, 0xFFE0E0E0);
    }
}
