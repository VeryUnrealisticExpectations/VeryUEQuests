package dev.veryue.vuequests.client.editor;

import net.minecraft.network.chat.Style;

public record NativePreviewGlyph(int codePoint, Style style, float advance, int sourceIndex) { }
