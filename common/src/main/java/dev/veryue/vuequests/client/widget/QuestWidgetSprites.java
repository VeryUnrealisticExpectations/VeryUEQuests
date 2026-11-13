package dev.veryue.vuequests.client.widget;

import net.minecraft.client.renderer.RenderPipelines;
import dev.veryue.vuequests.client.util.GuiSpriteRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.locale.Language;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestWidgetSprites {

    public static final int HEADER_HEIGHT = 17;
    public static final float TEXTURE_ALPHA = 0.6F;
    public static final Identifier SECTION_PANEL = sprite("section_panel");
    public static final Identifier CANVAS = sprite("canvas");
    public static final Identifier DETAILS_PANEL = sprite("details_panel");
    public static final Identifier CLOSE = sprite("close");
    public static final Identifier REWARD_UNCLAIMED = sprite("reward_unclaimed");
    public static final Identifier REWARD_CLAIMED = sprite("reward_claimed");
    public static final Identifier ACKNOWLEDGED = sprite("objective_acknowledged");
    public static final Identifier ACKNOWLEDGEMENT_PENDING = sprite("objective_acknowledgement_pending");
    public static final Identifier SECTION = sprite("section");
    public static final Identifier SECTION_SELECTED = sprite("section_selected");
    public static final Identifier SCROLLBAR = sprite("scrollbar");
    public static final Identifier OBJECTIVE_PROGRESS_BACKGROUND = sprite("objective_progress_background");
    public static final Identifier OBJECTIVE_PROGRESS_INCOMPLETE = sprite("objective_progress_incomplete");
    public static final Identifier OBJECTIVE_PROGRESS_COMPLETE = sprite("objective_progress_complete");
    public static final int OBJECTIVE_PROGRESS_WIDTH = 16;
    public static final int OBJECTIVE_PROGRESS_HEIGHT = 4;

    public static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, "quest_menu/" + name);
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height) {
        blit(graphics, sprite, x, y, width, height, 1);
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height, float brightness) {
        blit(graphics, sprite, x, y, width, height, brightness, brightness, brightness);
    }

    public static void blitTinted(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height, int tint) {
        blit(graphics, sprite, x, y, width, height,
                ARGB.red(tint) / 255F, ARGB.green(tint) / 255F, ARGB.blue(tint) / 255F);
    }

    public static void blitClaimBadge(GuiGraphicsExtractor graphics, int x, int y, boolean claimed) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, claimed ? REWARD_CLAIMED : REWARD_UNCLAIMED, x, y, 16, 16);
    }

    public static void blitObjectiveProgress(GuiGraphicsExtractor graphics, int x, int y, double progress) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OBJECTIVE_PROGRESS_BACKGROUND, x, y, OBJECTIVE_PROGRESS_WIDTH, OBJECTIVE_PROGRESS_HEIGHT);
        boolean complete = progress >= 1;
        int fillWidth = complete ? OBJECTIVE_PROGRESS_WIDTH : Mth.clamp((int) (progress * OBJECTIVE_PROGRESS_WIDTH), 0, OBJECTIVE_PROGRESS_WIDTH - 1);
        if (fillWidth > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, complete ? OBJECTIVE_PROGRESS_COMPLETE : OBJECTIVE_PROGRESS_INCOMPLETE,
                    OBJECTIVE_PROGRESS_WIDTH, OBJECTIVE_PROGRESS_HEIGHT, 0, 0, x, y, fillWidth, OBJECTIVE_PROGRESS_HEIGHT);
        }
    }

    public static void blitAcknowledgement(GuiGraphicsExtractor graphics, int x, int y, boolean acknowledged) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, acknowledged ? ACKNOWLEDGED : ACKNOWLEDGEMENT_PENDING, x, y + 2, 16, 13);
    }

    private static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height,
                             float red, float green, float blue) {
        GuiSpriteRenderer.blit(graphics, sprite, x, y, width, height, red, green, blue, TEXTURE_ALPHA);
    }

    public static void renderHeader(GuiGraphicsExtractor graphics, Font font, Component title, int x, int y, int width) {
        graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(title, Math.max(1, width - 8))),
                x + 4, y + 4, 0xFFE0E0E0);
    }
}
