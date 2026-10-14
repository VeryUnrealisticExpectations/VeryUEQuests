package dev.veryue.vuequests.client.mixin;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BakedGlyph.class)
public interface BakedGlyphAccessor {
    @Accessor("left") float vuequests$getLeft();
    @Accessor("right") float vuequests$getRight();
    @Accessor("up") float vuequests$getUp();
    @Accessor("down") float vuequests$getDown();
}
