package dev.veryue.vuequests.client.util;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class ClientTextUtil {

    public static Style styleAtWidth(Font font, FormattedCharSequence text, int width) {
        if (width < 0) return null;
        float[] remaining = {width};
        Style[] result = {null};
        text.accept((index, style, codepoint) -> {
            remaining[0] -= font.getSplitter().stringWidth(FormattedCharSequence.codepoint(codepoint, style));
            if (remaining[0] < 0) {
                result[0] = style;
                return false;
            }
            return true;
        });
        return result[0];
    }
}
