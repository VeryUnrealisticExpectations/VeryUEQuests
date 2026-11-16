package dev.veryue.vuequests.client.widget.panels;

import net.minecraft.client.input.MouseButtonEvent;

import net.minecraft.client.input.KeyEvent;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class SettingsPanel extends AbstractContainerWidget {

    private final List<AbstractWidget> controls = new ArrayList<>();
    private final Map<AbstractWidget, ScreenRectangle> positions = new LinkedHashMap<>();
    private int contentHeight;
    private double scroll;

    protected SettingsPanel(Component title) {
        super(0, 0, 1, 1, title);
    }

    protected abstract void rebuildControls();

    protected void clearControls() {
        setFocused(null);
        setDragging(false);
        controls.clear();
        positions.clear();
    }

    protected <T extends AbstractWidget> T addControl(T control, int x, int y, int width, int height) {
        controls.add(control);
        positions.put(control, new ScreenRectangle(x, y, width, height));
        return control;
    }

    protected int contentWidth() {
        return Math.max(1, getWidth() - 6);
    }

    protected void setContentHeight(int height) {
        contentHeight = height;
        positionControls();
    }

    private void positionControls() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - getHeight()));
        positions.forEach((control, bounds) -> control.setRectangle(bounds.width(), bounds.height(),
                getX() + bounds.left(), getY() + bounds.top() - (int) scroll));
    }

    @Override
    public void setRectangle(int width, int height, int x, int y) {
        boolean resized = width != getWidth() || height != getHeight();
        super.setRectangle(width, height, x, y);
        if (resized) rebuildControls();
        positionControls();
    }

    @Override
    public List<AbstractWidget> children() {
        return controls;
    }

    public boolean isEditingText() {
        return getFocused() instanceof EditBox box && box.isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) setFocused(null);
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return active && visible ? super.nextFocusPath(event) : null;
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        if (listener != getFocused()) super.setFocused(listener);
        if (listener instanceof AbstractWidget control && positions.containsKey(control)) {
            ScreenRectangle bounds = positions.get(control);
            if (bounds.top() < scroll) scroll = bounds.top();
            if (bounds.bottom() > scroll + getHeight()) scroll = bounds.bottom() - getHeight();
            positionControls();
        }
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        positionControls();
        boolean hovered = isMouseOver(mouseX, mouseY);
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        for (AbstractWidget control : controls) {
            if (control.getBottom() > getY() && control.getY() < getBottom()) {
                control.extractRenderState(graphics, hovered ? mouseX : -1, hovered ? mouseY : -1, partialTick);
            }
        }
        graphics.disableScissor();
        if (contentHeight > getHeight()) {
            int thumbHeight = Math.min(getHeight(), Math.max(8, getHeight() * getHeight() / contentHeight));
            int thumbY = getY() + (int) (scroll * (getHeight() - thumbHeight) / (contentHeight - getHeight()));
            graphics.fill(getRight() - 3, thumbY, getRight() - 1, thumbY + thumbHeight, 0xFF989898);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        if (!super.mouseClicked(event, doubleClick)) {
            setFocused(null);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        scroll -= deltaY * 22;
        positionControls();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        if (super.keyPressed(event)) return true;
        switch (keyCode) {
            case InputConstants.KEY_PAGEUP -> scroll -= getHeight();
            case InputConstants.KEY_PAGEDOWN -> scroll += getHeight();
            case InputConstants.KEY_HOME -> scroll = 0;
            case InputConstants.KEY_END -> scroll = contentHeight;
            default -> { return false; }
        }
        positionControls();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (getFocused() instanceof AbstractWidget control) control.updateNarration(output.nest());
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.settings_usage"));
    }
}
