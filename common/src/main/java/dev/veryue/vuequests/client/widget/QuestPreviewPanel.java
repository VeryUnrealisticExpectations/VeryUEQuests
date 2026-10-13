package dev.veryue.vuequests.client.widget;

import dev.veryue.vuequests.client.models.QuestPreviewEntry;
import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class QuestPreviewPanel extends AbstractScrollWidget {

    private static final int TEXT_COLOR = 0xFFD0D0D0;
    private static final int MUTED_COLOR = 0xFFAFAFAF;
    private static final int HEADING_COLOR = 0xFFE0E0E0;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final List<QuestPreviewEntry> EMPTY_ENTRIES = List.of();

    private final Font font;
    private Quest quest;
    private final List<QuestPreviewEntry> entries = new ArrayList<>();
    private int contentHeight;
    private boolean scrolling;

    public QuestPreviewPanel(Font font) {
        super(0, 0, 0, 0, Component.translatable("screen.vuequests.preview"));
        this.font = font;
    }

    @Override
    public void setRectangle(int width, int height, int x, int y) {
        super.setRectangle(width, height, x, y);
        measureContent();
    }

    public void setQuest(Quest quest) {
        if (this.quest == quest) {
            return;
        }
        this.quest = quest;
        entries.clear();
        if (quest != null) {
            addText(Component.literal(quest.display().title()), 0xFFFFFFFF);
            if (!quest.display().subtitle().isBlank()) {
                addText(Component.literal(quest.display().subtitle()), MUTED_COLOR);
            }
            if (!quest.display().description().isBlank()) {
                addText(Component.literal(quest.display().description()), TEXT_COLOR);
            }
            addText(Component.translatable("screen.vuequests.objectives"), HEADING_COLOR);
        }
        measureContent();
        setScrollAmount(0);
    }

    private void addText(Component text, int color) {
        entries.add(new QuestPreviewEntry(text, ItemStack.EMPTY, color));
    }

    private List<QuestPreviewEntry> visibleEntries() {
        return quest == null ? EMPTY_ENTRIES : entries;
    }

    private void measureContent() {
        contentHeight = visibleEntries().stream().mapToInt(entry -> entryHeight(entry)).sum();
        setScrollAmount(scrollAmount());
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        measureContent();
        renderBackground(graphics);
        graphics.enableScissor(getX(), contentTop(), getRight(), getBottom());
        renderContents(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        renderDecorations(graphics);
    }

    @Override
    protected void renderBackground(GuiGraphics graphics) {
        QuestWidgetSprites.renderPanel(graphics, font, this, QuestWidgetSprites.PREVIEW);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = contentTop() + 1 - (int) scrollAmount();
        for (QuestPreviewEntry entry : visibleEntries()) {
            boolean hasIcon = !entry.icon().isEmpty();
            if (hasIcon) {
                graphics.renderItem(entry.icon(), getX() + 4, y);
            }
            int textX = getX() + 4 + (hasIcon ? 20 : 0);
            int textY = y;
            for (FormattedCharSequence line : font.split(entry.text(), textWidth(hasIcon))) {
                graphics.drawString(font, line, textX, textY, entry.color());
                textY += font.lineHeight + 1;
            }
            y += entryHeight(entry);
        }
    }

    @Override
    protected void renderDecorations(GuiGraphics graphics) {
        if (scrollbarVisible()) {
            QuestWidgetSprites.blit(graphics, QuestWidgetSprites.SCROLLBAR, getRight() - SCROLLBAR_WIDTH, thumbY(), SCROLLBAR_WIDTH, thumbHeight());
        }
    }

    private int entryHeight(QuestPreviewEntry entry) {
        int textHeight = font.split(entry.text(), textWidth(!entry.icon().isEmpty())).size() * (font.lineHeight + 1);
        return Math.max(entry.icon().isEmpty() ? 0 : 16, textHeight);
    }

    private int textWidth(boolean hasIcon) {
        return Math.max(1, getWidth() - 12 - (hasIcon ? 20 : 0));
    }

    private int contentTop() {
        return getY() + QuestWidgetSprites.HEADER_HEIGHT;
    }

    private int viewportHeight() {
        return Math.max(0, getBottom() - contentTop() - 2);
    }

    @Override
    protected int getInnerHeight() {
        return contentHeight + QuestWidgetSprites.HEADER_HEIGHT;
    }

    @Override
    protected int getMaxScrollAmount() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    @Override
    protected double scrollRate() {
        return 18;
    }

    @Override
    protected boolean scrollbarVisible() {
        return getMaxScrollAmount() > 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }
        if (scrollbarVisible() && mouseX >= getRight() - SCROLLBAR_WIDTH && mouseY >= contentTop()) {
            scrolling = true;
            scrollTo(mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isActive() && isFocused() && button == 0 && scrolling) {
            scrollTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            scrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive() || !isFocused()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> setScrollAmount(scrollAmount() - viewportHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> setScrollAmount(scrollAmount() + viewportHeight());
            case GLFW.GLFW_KEY_HOME -> setScrollAmount(0);
            case GLFW.GLFW_KEY_END -> setScrollAmount(getMaxScrollAmount());
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    private int thumbHeight() {
        int track = Math.max(1, getBottom() - contentTop());
        return Mth.clamp(track * track / Math.max(1, contentHeight), Math.min(8, track), track);
    }

    private int thumbY() {
        return contentTop() + (int) (scrollAmount() * (getBottom() - contentTop() - thumbHeight()) / getMaxScrollAmount());
    }

    private void scrollTo(double mouseY) {
        int track = getBottom() - contentTop() - thumbHeight();
        setScrollAmount((mouseY - contentTop() - thumbHeight() / 2.0) * getMaxScrollAmount() / Math.max(1, track));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.HINT, visibleEntries().stream().map(QuestPreviewEntry::text).toArray(Component[]::new));
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.preview_usage"));
    }
}
