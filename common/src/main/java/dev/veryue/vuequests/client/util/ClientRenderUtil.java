package dev.veryue.vuequests.client.util;

import org.joml.Matrix3x2fStack;

import java.util.function.Consumer;

public class ClientRenderUtil {

    public static void renderInset(Matrix3x2fStack pose, RenderAnimation animation, boolean active, float distance,
                                   long durationMillis, Consumer<Float> render) {
        float inset = animation.value(active ? distance : 0, durationMillis);
        pose.pushMatrix();
        try {
            pose.translate(inset, 0);
            render.accept(inset);
        } finally {
            pose.popMatrix();
        }
    }

    public static void renderScaled(Matrix3x2fStack pose, RenderAnimation animation, boolean active, float activeScale,
                                    long durationMillis, float pivotX, float pivotY, Runnable render) {
        float scale = animation.value(active ? activeScale : 1, durationMillis);
        pose.pushMatrix();
        try {
            pose.translate(pivotX, pivotY);
            pose.scale(scale, scale);
            pose.translate(-pivotX, -pivotY);
            render.run();
        } finally {
            pose.popMatrix();
        }
    }
}
