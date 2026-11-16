package dev.veryue.vuequests.client.widget.lists;

import dev.veryue.vuequests.client.util.ClientRenderUtil;
import dev.veryue.vuequests.client.util.RenderAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class SettingsGroupList extends AbstractWidget {

    private static final int ROW_HEIGHT = 22;

    private final Font font;
    private final List<Component> groups;
    private final List<RenderAnimation> insets = new ArrayList<>();
    private final IntConsumer onSelect;
    private int selected;
    private double scroll;

    public SettingsGroupList(Font font, List<Component> groups, IntConsumer onSelect) {
        super(0, 0, 1, 1, Component.translatable("screen.vuequests.settings"));
        this.font = font;
        this.groups = groups;
        this.onSelect = onSelect;
        groups.forEach(group -> insets.add(new RenderAnimation(0)));
    }

    public void select(int index) {
        if (groups.isEmpty()) return;
        selected = Mth.clamp(index, 0, groups.size() - 1);
        setMessage(groups.get(selected));
        if (selected * ROW_HEIGHT < scroll) scroll = selected * ROW_HEIGHT;
        if ((selected + 1) * ROW_HEIGHT > scroll + getHeight()) scroll = (selected + 1) * ROW_HEIGHT - getHeight();
        onSelect.accept(selected);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        scroll = Mth.clamp(scroll, 0, Math.max(0, groups.size() * ROW_HEIGHT - getHeight()));
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        for (int index = 0; index < groups.size(); index++) {
            int y = getY() + index * ROW_HEIGHT - (int) scroll;
            if (y + ROW_HEIGHT <= getY() || y >= getBottom()) continue;
            boolean current = index == selected;
            boolean hovered = isMouseOver(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_HEIGHT;
            Component label = groups.get(index).copy().withStyle(style -> style.withBold(current));
            ClientRenderUtil.renderInset(graphics.pose(), insets.get(index), current || hovered, 6, 160, inset ->
                    graphics.drawString(font, Language.getInstance().getVisualOrder(
                                    font.substrByWidth(label, Math.max(1, getWidth() - 8 - Mth.ceil(inset)))),
                            getX() + 3, y + 7, current ? 0xFFFFFFFF : 0xFFCCCCCC));
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        int index = (int) ((mouseY - getY() + scroll) / ROW_HEIGHT);
        if (index < groups.size()) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            select(index);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scroll = Mth.clamp(scroll - deltaY * ROW_HEIGHT, 0, Math.max(0, groups.size() * ROW_HEIGHT - getHeight()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int index = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> selected - 1;
            case GLFW.GLFW_KEY_DOWN -> selected + 1;
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> groups.size() - 1;
            default -> -1;
        };
        if (index < 0 && keyCode != GLFW.GLFW_KEY_UP) return super.keyPressed(keyCode, scanCode, modifiers);
        select(index);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.settings_groups_usage"));
    }
}
