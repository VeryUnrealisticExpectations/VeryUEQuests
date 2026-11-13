package dev.veryue.vuequests.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
    @Accessor("guiRenderState") GuiRenderState vuequests$getRenderState();
    @Invoker("componentHoverEffect") void vuequests$componentHoverEffect(Font font, Style style, int x, int y);
}
