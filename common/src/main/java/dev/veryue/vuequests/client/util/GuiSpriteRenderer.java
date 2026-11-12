package dev.veryue.vuequests.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;

// AFTER 1.21.1 WE DO NOT NEED THIS CLASS!!!!
public class GuiSpriteRenderer {

    private static final GuiSpriteRenderer RENDERER = new GuiSpriteRenderer();

    private VertexConsumer vertices;
    private Matrix4f pose;
    private float minU;
    private float minV;
    private float spanU;
    private float spanV;
    private int color;

    public static void blit(GuiGraphics graphics, ResourceLocation location, int x, int y, int width, int height, int color) {
        blit(graphics, location, x, y, width, height, FastColor.ARGB32.red(color) / 255F,
                FastColor.ARGB32.green(color) / 255F, FastColor.ARGB32.blue(color) / 255F, FastColor.ARGB32.alpha(color) / 255F);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation location, int x, int y, int width, int height,
                            float red, float green, float blue, float alpha) {
        if (width <= 0 || height <= 0) return;
        RenderSystem.assertOnRenderThread();
        TextureAtlasSprite sprite = Minecraft.getInstance().getGuiSprites().getSprite(location);
        GuiSpriteScaling scaling = Minecraft.getInstance().getGuiSprites().getSpriteScaling(sprite);
        graphics.flush();
        graphics.setColor(red, green, blue, alpha);
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            // Vanilla 1.21.1 uploads each tile separately all slices share one texture and can share one draw.
            RENDERER.render(buffer, graphics.pose().last().pose(), sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                    0xFFFFFFFF, scaling, x, y, width, height);
            MeshData mesh = buffer.buildOrThrow();
            ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
            profiler.incrementCounter("sprite_batches");
            profiler.incrementCounter("sprite_quads", mesh.drawState().vertexCount() / 4);
            BufferUploader.drawWithShader(mesh);
        } finally {
            graphics.setColor(1, 1, 1, 1);
        }
    }

    void render(VertexConsumer vertices, Matrix4f pose, float minU, float minV, float maxU, float maxV, int color,
                GuiSpriteScaling scaling, int x, int y, int width, int height) {
        this.vertices = vertices;
        this.pose = pose;
        this.minU = minU;
        this.minV = minV;
        spanU = maxU - minU;
        spanV = maxV - minV;
        this.color = color;
        try {
            render(scaling, x, y, width, height);
        } finally {
            this.vertices = null;
            this.pose = null;
        }
    }

    private void render(GuiSpriteScaling scaling, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (scaling instanceof GuiSpriteScaling.Tile tile) {
            tile(x, y, width, height, 0, 0, tile.width(), tile.height(), tile.width(), tile.height());
        } else if (scaling instanceof GuiSpriteScaling.NineSlice slice) {
            int left = width == slice.width() ? 0 : Math.min(slice.border().left(), width / 2);
            int right = width == slice.width() ? 0 : Math.min(slice.border().right(), width / 2);
            int top = height == slice.height() ? 0 : Math.min(slice.border().top(), height / 2);
            int bottom = height == slice.height() ? 0 : Math.min(slice.border().bottom(), height / 2);
            for (int row = 0; row < 3; row++) {
                int offsetY = row == 0 ? 0 : row == 1 ? top : height - bottom;
                int sourceY = row == 0 ? 0 : row == 1 ? top : slice.height() - bottom;
                int targetHeight = row == 0 ? top : row == 1 ? height - top - bottom : bottom;
                int tileHeight = row == 1 ? slice.height() - top - bottom : targetHeight;
                for (int column = 0; column < 3; column++) {
                    int offsetX = column == 0 ? 0 : column == 1 ? left : width - right;
                    int sourceX = column == 0 ? 0 : column == 1 ? left : slice.width() - right;
                    int targetWidth = column == 0 ? left : column == 1 ? width - left - right : right;
                    int tileWidth = column == 1 ? slice.width() - left - right : targetWidth;
                    tile(x + offsetX, y + offsetY, targetWidth, targetHeight, sourceX, sourceY,
                            tileWidth, tileHeight, slice.width(), slice.height());
                }
            }
        } else {
            quad(x, y, width, height, 0, 0, 1, 1);
        }
    }

    private void tile(int x, int y, int width, int height, int sourceX, int sourceY,
                      int tileWidth, int tileHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) return;
        for (int offsetX = 0; offsetX < width; offsetX += tileWidth) {
            int quadWidth = Math.min(tileWidth, width - offsetX);
            for (int offsetY = 0; offsetY < height; offsetY += tileHeight) {
                int quadHeight = Math.min(tileHeight, height - offsetY);
                quad(x + offsetX, y + offsetY, quadWidth, quadHeight,
                        sourceX / (float) textureWidth, sourceY / (float) textureHeight,
                        (sourceX + quadWidth) / (float) textureWidth, (sourceY + quadHeight) / (float) textureHeight);
            }
        }
    }

    private void quad(int x, int y, int width, int height, float u0, float v0, float u1, float v1) {
        u0 = minU + spanU * u0;
        v0 = minV + spanV * v0;
        u1 = minU + spanU * u1;
        v1 = minV + spanV * v1;
        vertices.addVertex(pose, x, y, 0).setUv(u0, v0).setColor(color);
        vertices.addVertex(pose, x, y + height, 0).setUv(u0, v1).setColor(color);
        vertices.addVertex(pose, x + width, y + height, 0).setUv(u1, v1).setColor(color);
        vertices.addVertex(pose, x + width, y, 0).setUv(u1, v0).setColor(color);
    }
}
