package dev.veryue.vuequests.client.mixin;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Accessor("fontSets") Map<ResourceLocation, FontSet> vuequests$getFontSets();
    @Invoker("getFontSetRaw") FontSet vuequests$getFontSet(ResourceLocation id);
}
