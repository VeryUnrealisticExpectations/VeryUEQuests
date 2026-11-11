package dev.veryue.vuequests.client.util;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.function.Consumer;

public class ClientRenderUtil {

    public static void renderInset(PoseStack pose, RenderAnimation animation, boolean active, float distance,
                                   long durationMillis, Consumer<Float> render) {
        float inset = animation.value(active ? distance : 0, durationMillis);
        pose.pushPose();
        try {
            pose.translate(inset, 0, 0);
            render.accept(inset);
        } finally {
            pose.popPose();
        }
    }

    public static void renderScaled(PoseStack pose, RenderAnimation animation, boolean active, float activeScale,
                                    long durationMillis, float pivotX, float pivotY, Runnable render) {
        float scale = animation.value(active ? activeScale : 1, durationMillis);
        pose.pushPose();
        try {
            pose.translate(pivotX, pivotY, 0);
            pose.scale(scale, scale, 1);
            pose.translate(-pivotX, -pivotY, 0);
            render.run();
        } finally {
            pose.popPose();
        }
    }
}
