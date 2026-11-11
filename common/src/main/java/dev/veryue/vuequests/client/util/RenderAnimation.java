package dev.veryue.vuequests.client.util;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class RenderAnimation {

    private final LongSupplier clock;
    private float start;
    private float target;
    private long startedAt;
    private long durationNanos;

    public RenderAnimation(float initialValue) {
        this(initialValue, System::nanoTime);
    }

    public RenderAnimation(float initialValue, LongSupplier nanoClock) {
        clock = nanoClock;
        start = initialValue;
        target = initialValue;
    }

    public float value(float nextTarget, long durationMillis) {
        if (durationMillis < 0) throw new IllegalArgumentException("Animation duration must not be negative.");
        long now = clock.getAsLong();
        float current = valueAt(now);
        long nextDuration = TimeUnit.MILLISECONDS.toNanos(durationMillis);
        if (nextTarget != target || nextDuration != durationNanos) {
            start = current;
            target = nextTarget;
            startedAt = now;
            durationNanos = nextDuration;
        }
        return valueAt(now);
    }

    private float valueAt(long now) {
        if (durationNanos == 0) return target;
        float progress = (float) Math.clamp((now - startedAt) / (double) durationNanos, 0, 1);
        float eased = progress * progress * (3 - 2 * progress);
        return start + (target - start) * eased;
    }
}
