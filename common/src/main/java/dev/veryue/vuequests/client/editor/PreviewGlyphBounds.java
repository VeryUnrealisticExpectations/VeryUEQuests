package dev.veryue.vuequests.client.editor;

public record PreviewGlyphBounds(float left, float right, float top, float bottom) {
    public static PreviewGlyphBounds measure(float left, float right, float up, float down, float advance,
                                             float boldOffset, boolean italic, boolean decoration) {
        float upperShear = italic ? 1 - 0.25F * up : 0;
        float lowerShear = italic ? 1 - 0.25F * down : 0;
        float minimum = left + Math.min(upperShear, lowerShear);
        float maximum = right + Math.max(upperShear, lowerShear) + boldOffset - advance;
        return new PreviewGlyphBounds(Math.min(minimum, decoration ? -1 : 0), Math.max(0, maximum),
                Math.min(0, up), Math.max(9, down));
    }
}
