package dev.veryue.vuequests.client.widget;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class QuestSectionPanel extends QuestPanel {

    public static final int COLLAPSED_WIDTH = 24;
    private static final int ROW_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 4;

    private final List<QuestSectionButton> entries = new ArrayList<>();
    private final Consumer<Section> onSelect;
    private int scroll;
    private boolean scrolling;
    private boolean collapsed;

    public QuestSectionPanel(Font font, Consumer<Section> onSelect) {
        super(font, Component.translatable("screen.vuequests.section_title"), QuestWidgetSprites.SECTION_PANEL);
        this.onSelect = onSelect;
    }

    public void setSections(List<Section> sections) {
        setFocused(null);
        entries.clear();
        sections.forEach(section -> {
            entries.add(new QuestSectionButton(font, section, onSelect));
        });
        setScroll(scroll);
    }

    public void setSelected(UUID selected) {
        entries.forEach(entry -> entry.setSelected(entry.getSection().sectionUUID().equals(selected)));
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public int headerWidth() {
        return collapsed ? Math.max(getWidth(), font.width(getMessage()) + 8) : getWidth();
    }

    @Override
    public List<QuestSectionButton> children() {
        return entries;
    }

    @Override
    public void setRectangle(int width, int height, int x, int y) {
        super.setRectangle(width, height, x, y);
        setScroll(scroll);
    }

    private void positionEntries() {
        int rowWidth = getWidth() - (maxScroll() > 0 ? SCROLLBAR_WIDTH : 0);
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRectangle(rowWidth, ROW_HEIGHT, getX(), contentTop() + i * ROW_HEIGHT - scroll);
        }
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics) {
        if (!collapsed) {
            super.extractBackground(graphics);
            return;
        }
        QuestWidgetSprites.blit(graphics, QuestWidgetSprites.SECTION_PANEL, getX(), contentTop(), getWidth(), getBottom() - contentTop());
        int headerWidth = headerWidth();
        QuestWidgetSprites.blit(graphics, QuestWidgetSprites.SECTION_PANEL, getX(), getY(), headerWidth, QuestWidgetSprites.HEADER_HEIGHT);
        QuestWidgetSprites.renderHeader(graphics, font, getMessage(), getX(), getY(), headerWidth);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Component hoveredName = null;
        graphics.enableScissor(getX(), contentTop(), getRight(), getBottom());
        if (entries.isEmpty() && !collapsed) {
            graphics.textWithWordWrap(font, Component.translatable("screen.vuequests.no_sections"), getX() + 4, contentTop() + 2,
                    Math.max(1, getWidth() - 8), 0xFFAFAFAF);
        }
        for (QuestSectionButton entry : entries) {
            if (entry.getBottom() > contentTop() && entry.getY() < getBottom()) {
                entry.extractRenderState(graphics, mouseX, mouseY >= contentTop() ? mouseY : -1, partialTick);
                if (collapsed && entry.isHovered()) {
                    hoveredName = entry.getMessage();
                }
            }
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            QuestWidgetSprites.blit(graphics, QuestWidgetSprites.SCROLLBAR, getRight() - SCROLLBAR_WIDTH, thumbY(), SCROLLBAR_WIDTH, thumbHeight());
        }
        if (hoveredName != null) {
            graphics.setTooltipForNextFrame(font, Tooltip.splitTooltip(Minecraft.getInstance(), hoveredName), mouseX, mouseY);
        }
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        if (focused instanceof QuestSectionButton entry) {
            if (entry.getY() < contentTop()) {
                setScroll(scroll + entry.getY() - contentTop());
            } else if (entry.getBottom() > getBottom()) {
                setScroll(scroll + entry.getBottom() - getBottom());
            }
            onSelect.accept(entry.getSection());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!isMouseOver(mouseX, mouseY) || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (mouseY < contentTop()) {
            setFocused(true);
            return true;
        }
        if (maxScroll() > 0 && mouseX >= getRight() - SCROLLBAR_WIDTH) {
            scrolling = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && scrolling) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && scrolling) {
            scrolling = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        setScroll(scroll - (int) (scrollY * ROW_HEIGHT));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        if (!isActive() || !isFocused() || entries.isEmpty()) {
            return false;
        }
        int index = entries.indexOf(getFocused());
        int page = Math.max(1, (getBottom() - contentTop()) / ROW_HEIGHT);
        int next = switch (keyCode) {
            case InputConstants.KEY_HOME -> 0;
            case InputConstants.KEY_END -> entries.size() - 1;
            case InputConstants.KEY_PAGEUP -> Math.max(0, index - page);
            case InputConstants.KEY_PAGEDOWN -> Math.min(entries.size() - 1, index + page);
            default -> -1;
        };
        if (next >= 0) {
            setFocused(entries.get(next));
            return true;
        }
        return super.keyPressed(event);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_HEIGHT - Math.max(0, getBottom() - contentTop()));
    }

    private void setScroll(int amount) {
        scroll = Mth.clamp(amount, 0, maxScroll());
        positionEntries();
    }

    private int thumbHeight() {
        int track = getBottom() - contentTop();
        return Math.max(8, track * track / (entries.size() * ROW_HEIGHT));
    }

    private int thumbY() {
        return contentTop() + scroll * (getBottom() - contentTop() - thumbHeight()) / maxScroll();
    }

    private void scrollTo(double mouseY) {
        int track = getBottom() - contentTop() - thumbHeight();
        setScroll((int) ((mouseY - contentTop() - thumbHeight() / 2.0) * maxScroll() / Math.max(1, track)));
    }
}
