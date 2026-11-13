package dev.veryue.vuequests.client.widget;

import org.joml.Matrix3x2fStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;
import net.minecraft.util.Mth;
import dev.veryue.vuequests.client.mixin.GuiGraphicsExtractorAccessor;

public record QuestDependencyLine(QuestNodeWidget prerequisite, QuestNodeWidget dependent) {

    public boolean intersects(ScreenRectangle viewport, double offsetX, double offsetY) {
        double startX = prerequisite.getX() + prerequisite.getWidth() / 2.0 + offsetX;
        double startY = prerequisite.getY() + prerequisite.getHeight() / 2.0 + offsetY;
        double endX = dependent.getX() + dependent.getWidth() / 2.0 + offsetX;
        double endY = dependent.getY() + dependent.getHeight() / 2.0 + offsetY;
        double padding = 6 * prerequisite.getWidth() / (double) QuestNodeWidget.SIZE + 1;
        return Math.max(startX, endX) + padding >= viewport.left() && Math.min(startX, endX) - padding <= viewport.right()
                && Math.max(startY, endY) + padding >= viewport.top() && Math.min(startY, endY) - padding <= viewport.bottom();
    }

    public void render(GuiGraphicsExtractor graphics, ScreenRectangle scissor) {
        double startX = prerequisite.getX() + prerequisite.getWidth() / 2.0;
        double startY = prerequisite.getY() + prerequisite.getHeight() / 2.0;
        double deltaX = dependent.getX() + dependent.getWidth() / 2.0 - startX;
        double deltaY = dependent.getY() + dependent.getHeight() / 2.0 - startY;
        double distance = Math.hypot(deltaX, deltaY);
        if (distance == 0) {
            return;
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate((float) startX, (float) startY);
            pose.rotate((float) Math.atan2(deltaY, deltaX));
            float zoom = prerequisite.getWidth() / (float) QuestNodeWidget.SIZE;
            pose.scale(zoom, zoom);
            float feather = (float) (1.0 / (Minecraft.getInstance().getWindow().getGuiScale() * zoom));
            int padding = Mth.ceil(6 + feather);
            ScreenRectangle bounds = new ScreenRectangle(-padding, -padding, Mth.ceil(distance / zoom) + padding * 2,
                    padding * 2).transformMaxBounds(pose).intersection(scissor);
            ((GuiGraphicsExtractorAccessor) graphics).vuequests$getRenderState().addGuiElement(
                    new QuestDependencyLineRenderState(new Matrix3x2f(pose), (float) distance / zoom, feather, scissor, bounds));
        } finally {
            pose.popMatrix();
        }
    }

}
