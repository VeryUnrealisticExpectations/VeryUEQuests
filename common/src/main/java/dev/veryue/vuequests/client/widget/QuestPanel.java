package dev.veryue.vuequests.client.widget;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class QuestPanel extends AbstractContainerWidget {

    protected final Font font;
    private final ResourceLocation sprite;
    private boolean panelFocused;

    public QuestPanel(Font font, Component title, ResourceLocation sprite) {
        super(0, 0, 0, 0, title);
        this.font = font;
        this.sprite = sprite;
    }

    protected int contentTop() {
        return getY() + QuestWidgetSprites.HEADER_HEIGHT;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderContents(graphics, mouseX, mouseY, partialTick);
    }

    protected void renderBackground(GuiGraphics graphics) {
        blitBackground(graphics);
        renderHeader(graphics);
    }

    protected void blitBackground(GuiGraphics graphics) {
        QuestWidgetSprites.blit(graphics, sprite, getX(), getY(), getWidth(), getHeight());
    }

    protected void renderHeader(GuiGraphics graphics) {
        QuestWidgetSprites.renderHeader(graphics, font, getMessage(), getX(), getY(), getWidth());
    }

    protected abstract void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }
        if (!super.mouseClicked(mouseX, mouseY, button)) {
            setFocused(true);
        }
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        panelFocused = focused;
        if (!focused) {
            setFocused((GuiEventListener) null);
        }
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        panelFocused = focused != null;
        super.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return panelFocused || super.isFocused();
    }

    @Override
    public ComponentPath getCurrentFocusPath() {
        if (getFocused() == null) {
            return isFocused() ? ComponentPath.leaf(this) : null;
        }
        return super.getCurrentFocusPath();
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!isActive()) {
            return null;
        }
        if (children().isEmpty()) {
            return isFocused() ? null : ComponentPath.leaf(this);
        }
        return super.nextFocusPath(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        GuiEventListener target = getFocused();
        if (target == null) {
            target = children().stream().filter(child -> child instanceof NarratableEntry entry
                    && entry.narrationPriority() == NarrationPriority.HOVERED).findFirst().orElse(null);
        }
        if (target instanceof NarratableEntry entry) {
            output.add(NarratedElementType.POSITION, Component.translatable("narration.vuequests.position", children().indexOf(target) + 1, children().size()));
            entry.updateNarration(output.nest());
        }
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.panel_usage"));
    }
}
