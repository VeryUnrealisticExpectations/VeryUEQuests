package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Profiler;

public abstract class QuestPanel extends AbstractContainerWidget {

    protected final Font font;
    private final Identifier sprite;
    private boolean panelFocused;

    public QuestPanel(Font font, Component title, Identifier sprite) {
        super(0, 0, 0, 0, title);
        this.font = font;
        this.sprite = sprite;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    protected int contentTop() {
        return getY() + QuestWidgetSprites.HEADER_HEIGHT;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("panel_background");
        try {
            extractBackground(graphics);
            profiler.popPush("panel_contents");
            extractContents(graphics, mouseX, mouseY, partialTick);
        } finally {
            profiler.pop();
        }
    }

    protected void extractBackground(GuiGraphicsExtractor graphics) {
        blitBackground(graphics);
        renderHeader(graphics);
    }

    protected void blitBackground(GuiGraphicsExtractor graphics) {
        QuestWidgetSprites.blit(graphics, sprite, getX(), getY(), getWidth(), getHeight());
    }

    protected void renderHeader(GuiGraphicsExtractor graphics) {
        QuestWidgetSprites.renderHeader(graphics, font, getMessage(), getX(), getY(), getWidth());
    }

    protected abstract void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!isMouseOver(mouseX, mouseY) || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (!super.mouseClicked(event, doubleClick)) {
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
