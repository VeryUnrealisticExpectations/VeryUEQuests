package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public record QuestDependencyLineRenderState(Matrix3x2fc pose, float length, float feather,
                                             ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        stroke(vertices, 0, 0, length, 0, 1.5F, 0xFF202020);
        stroke(vertices, 0, 0, length, 0, 0.75F, 0xFFAFAFAF);
        if (length > QuestNodeWidget.SIZE * 2) {
            float tip = length / 2 + 3;
            float tail = tip - 6;
            stroke(vertices, tail, -4, tip, 0, 1.75F, 0xFF202020);
            stroke(vertices, tail, 4, tip, 0, 1.75F, 0xFF202020);
            stroke(vertices, tail, -4, tip, 0, 1, 0xFFF2D080);
            stroke(vertices, tail, 4, tip, 0, 1, 0xFFF2D080);
        }
    }

    private void stroke(VertexConsumer vertices, float startX, float startY, float endX, float endY, float halfWidth, int color) {
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float strokeLength = (float) Math.hypot(deltaX, deltaY);
        Matrix3x2f transform = new Matrix3x2f(pose).translate(startX, startY).rotate((float) Math.atan2(deltaY, deltaX));
        float[] x = {-feather, 0, strokeLength, strokeLength + feather};
        float[] y = {-halfWidth - feather, -halfWidth, halfWidth, halfWidth + feather};
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                vertex(vertices, transform, x, y, column + 1, row + 1, color);
                vertex(vertices, transform, x, y, column + 1, row, color);
                vertex(vertices, transform, x, y, column, row, color);
                vertex(vertices, transform, x, y, column, row + 1, color);
            }
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix3x2fc pose, float[] x, float[] y, int column, int row, int color) {
        int vertexColor = column == 0 || column == 3 || row == 0 || row == 3 ? color & 0x00FFFFFF : color;
        vertices.addVertexWith2DPose(pose, x[column], y[row]).setColor(vertexColor);
    }
}
