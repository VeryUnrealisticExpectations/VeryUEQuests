package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

public record QuestDependencyLine(QuestNodeWidget prerequisite, QuestNodeWidget dependent) {

    private static final int BORDER_COLOR = 0xFF202020;
    private static final int LINE_COLOR = 0xFFAFAFAF;

    public void render(GuiGraphics graphics) {
        double startX = prerequisite.getX() + QuestNodeWidget.SIZE / 2.0;
        double startY = prerequisite.getY() + QuestNodeWidget.SIZE / 2.0;
        double deltaX = dependent.getX() + QuestNodeWidget.SIZE / 2.0 - startX;
        double deltaY = dependent.getY() + QuestNodeWidget.SIZE / 2.0 - startY;
        double distance = Math.hypot(deltaX, deltaY);
        if (distance == 0) {
            return;
        }

        double edgeInset = distance * (QuestNodeWidget.SIZE / 2.0) / Math.max(Math.abs(deltaX), Math.abs(deltaY));
        double length = distance - 2 * edgeInset;
        if (length <= 0) {
            return;
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(startX + deltaX * edgeInset / distance, startY + deltaY * edgeInset / distance, 0);
            pose.mulPose(Axis.ZP.rotation((float) Math.atan2(deltaY, deltaX)));
            pose.scale((float) length, 1, 1);
            graphics.fill(0, -2, 1, 2, BORDER_COLOR);
            graphics.fill(0, -1, 1, 1, LINE_COLOR);
        } finally {
            pose.popPose();
        }
    }
}
