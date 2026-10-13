package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

public record QuestDependencyLine(QuestNodeWidget prerequisite, QuestNodeWidget dependent) {

    private static final int BORDER_COLOR = 0xFF202020;
    private static final int LINE_COLOR = 0xFFAFAFAF;

    private static final int ARROW_COLOR = 0xFFF2D080;
    private static final float ARROW_LENGTH = 6;
    private static final float ARROW_HEIGHT = 4;

    public void render(GuiGraphics graphics) {
        double startX = prerequisite.getX() + prerequisite.getWidth() / 2.0;
        double startY = prerequisite.getY() + prerequisite.getHeight() / 2.0;
        double deltaX = dependent.getX() + dependent.getWidth() / 2.0 - startX;
        double deltaY = dependent.getY() + dependent.getHeight() / 2.0 - startY;
        double distance = Math.hypot(deltaX, deltaY);
        if (distance == 0) {
            return;
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(startX, startY, -1);
            pose.mulPose(Axis.ZP.rotation((float) Math.atan2(deltaY, deltaX)));
            float zoom = prerequisite.getWidth() / (float) QuestNodeWidget.SIZE;
            pose.scale(zoom, zoom, 1);
            float feather = (float) (1.0 / (Minecraft.getInstance().getWindow().getGuiScale() * zoom));
            render(graphics, (float) distance / zoom, feather, BORDER_COLOR, LINE_COLOR);
        } finally {
            pose.popPose();
        }
    }

    private void render(GuiGraphics graphics, float length, float feather, int borderColor, int lineColor) {
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        Matrix4f pose = graphics.pose().last().pose();
        stroke(vertices, pose, 0, 0, length, 0, 1.5F, feather, borderColor);
        stroke(vertices, pose, 0, 0, length, 0, 0.75F, feather, lineColor);

        if (length > QuestNodeWidget.SIZE * 2) {
            float tip = length / 2 + ARROW_LENGTH / 2;
            float tail = tip - ARROW_LENGTH;
            stroke(vertices, pose, tail, -ARROW_HEIGHT, tip, 0, 1.75F, feather, borderColor);
            stroke(vertices, pose, tail, ARROW_HEIGHT, tip, 0, 1.75F, feather, borderColor);
            stroke(vertices, pose, tail, -ARROW_HEIGHT, tip, 0, 1, feather, ARROW_COLOR);
            stroke(vertices, pose, tail, ARROW_HEIGHT, tip, 0, 1, feather, ARROW_COLOR);
        }
    }

    private void stroke(VertexConsumer vertices, Matrix4f pose, float startX, float startY, float endX, float endY,
                               float halfWidth, float feather, int color) {
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float length = (float) Math.hypot(deltaX, deltaY);
        Matrix4f transform = new Matrix4f(pose).translate(startX, startY, 0).rotateZ((float) Math.atan2(deltaY, deltaX));
        float[] x = {-feather, 0, length, length + feather};
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

    private static void vertex(VertexConsumer vertices, Matrix4f pose, float[] x, float[] y, int column, int row, int color) {
        int vertexColor = column == 0 || column == 3 || row == 0 || row == 3 ? color & 0x00FFFFFF : color;
        vertices.addVertex(pose, x[column], y[row], 0).setColor(vertexColor);
    }
}
