package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.util.resource.RenderableResource;
import de.keksuccino.fancymenu.util.resource.ResourceSupplier;
import de.keksuccino.fancymenu.util.resource.resources.texture.ITexture;
import dev.veryue.vuequests.client.widget.LinearQuestHeadingDecoration;
import dev.veryue.vuequests.client.widget.LinearQuestSprites;
import net.minecraft.client.gui.GuiGraphics;

record FancyMenuHeadingTexture(ResourceSupplier<ITexture> supplier) implements LinearQuestHeadingDecoration {

    @Override
    public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        ITexture texture = supplier.get();
        if (texture == null || texture.getWidth() < 1 || texture.getHeight() < 1
                || RenderableResource.MISSING_TEXTURE_LOCATION.equals(texture.getResourceLocation())) {
            graphics.blitSprite(LinearQuestSprites.BREAKLINE, x, y, width, height);
            return;
        }
        graphics.blit(texture.getResourceLocation(), x, y, width, height, 0, 0,
                texture.getWidth(), texture.getHeight(), texture.getWidth(), texture.getHeight());
    }
}
