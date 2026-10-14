package dev.veryue.vuequests.client.editor;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;

public record NativePreviewFrame(JsonObject response, NativeImage image) implements AutoCloseable {
    @Override public void close() { image.close(); }
}
