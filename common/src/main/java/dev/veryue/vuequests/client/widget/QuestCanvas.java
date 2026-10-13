package dev.veryue.vuequests.client.widget;

import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class QuestCanvas extends QuestPanel {

    private final List<QuestNodeWidget> nodes = new ArrayList<>();
    private final List<QuestDependencyLine> dependencyLines = new ArrayList<>();
    private final Consumer<Quest> onPreview;
    private Section section;
    private double panX;
    private double panY;
    private boolean panning;

    public QuestCanvas(Font font, Consumer<Quest> onPreview) {
        super(font, Component.translatable("screen.vuequests.quest_menu"), QuestWidgetSprites.CANVAS);
        this.onPreview = onPreview;
    }

    public void setSection(Section section) {
        this.section = section;
        setFocused(null);
        nodes.clear();
        dependencyLines.clear();
        if (section != null) {
            Map<UUID, QuestNodeWidget> nodeIndex = new HashMap<>();
            for (Quest quest : section.questList()) {
                QuestNodeWidget node = new QuestNodeWidget(quest, onPreview);
                nodes.add(node);
                nodeIndex.put(quest.questId(), node);
            }
            for (QuestNodeWidget node : nodes) {
                for (UUID dependency : node.getQuest().dependencies()) {
                    QuestNodeWidget prerequisite = nodeIndex.get(dependency);
                    if (prerequisite != null && prerequisite != node) {
                        dependencyLines.add(new QuestDependencyLine(prerequisite, node));
                    }
                }
            }
        }
        setMessage(section == null ? Component.translatable("screen.vuequests.quest_menu") : Component.literal(section.name()));
        recenter();
    }

    public Section getSection() {
        return section;
    }

    @Override
    public List<QuestNodeWidget> children() {
        return nodes;
    }

    @Override
    public void setRectangle(int width, int height, int x, int y) {
        super.setRectangle(width, height, x, y);
        positionNodes();
        panning = false;
    }

    public ScreenRectangle viewport() {
        return new ScreenRectangle(getX(), contentTop(), getWidth(), Math.max(0, getBottom() - contentTop() - 11));
    }

    private void recenter() {
        panX = 0;
        panY = 0;
        panning = false;
        positionNodes();
    }

    private void positionNodes() {
        ScreenRectangle viewport = viewport();
        for (QuestNodeWidget node : nodes) {
            node.setX((int) Math.round(viewport.left() + viewport.width() / 2.0 + panX + node.getQuest().position().x() - QuestNodeWidget.SIZE / 2.0));
            node.setY((int) Math.round(viewport.top() + viewport.height() / 2.0 + panY + node.getQuest().position().y() - QuestNodeWidget.SIZE / 2.0));
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenRectangle viewport = viewport();
        Quest hovered = questAt(mouseX, mouseY);
        if (hovered != null) {
            onPreview.accept(hovered);
        }
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        for (QuestDependencyLine line : dependencyLines) {
            line.render(graphics);
        }
        graphics.flush();
        boolean hoverAllowed = !panning && viewport.containsPoint(mouseX, mouseY);
        for (QuestNodeWidget node : nodes) {
            if (viewport.overlaps(node.getRectangle())) {
                node.render(graphics, hoverAllowed ? mouseX : -1, hoverAllowed ? mouseY : -1, partialTick);
            }
        }
        if (nodes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.vuequests.no_quests"),
                    viewport.left() + viewport.width() / 2, viewport.top() + viewport.height() / 2, 0xFFAFAFAF);
        }
        graphics.disableScissor();
    }

    public Quest questAt(double mouseX, double mouseY) {
        QuestNodeWidget node = nodeAt(mouseX, mouseY);
        return panning || node == null ? null : node.getQuest();
    }

    private QuestNodeWidget nodeAt(double mouseX, double mouseY) {
        if (!isActive() || !viewport().containsPoint((int) mouseX, (int) mouseY)) {
            return null;
        }
        for (int i = nodes.size() - 1; i >= 0; i--) {
            QuestNodeWidget node = nodes.get(i);
            if (node.isMouseOver(mouseX, mouseY)) {
                return node;
            }
        }
        return null;
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        if (focused instanceof QuestNodeWidget node) {
            if (!panning) {
                ScreenRectangle viewport = viewport();
                if (node.getX() < viewport.left() || node.getRight() > viewport.right()) {
                    panX += viewport.left() + viewport.width() / 2.0 - node.getX() - node.getWidth() / 2.0;
                }
                if (node.getY() < viewport.top() || node.getBottom() > viewport.bottom()) {
                    panY += viewport.top() + viewport.height() / 2.0 - node.getY() - node.getHeight() / 2.0;
                }
                positionNodes();
            }
            onPreview.accept(node.getQuest());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }
        QuestNodeWidget node = nodeAt(mouseX, mouseY);
        panning = true;
        if (node != null) {
            setFocused(node);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && panning && isActive()) {
            panX += dragX;
            panY += dragY;
            positionNodes();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && panning) {
            panning = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive() || !isFocused()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            recenter();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> panX += 24;
                case GLFW.GLFW_KEY_RIGHT -> panX -= 24;
                case GLFW.GLFW_KEY_UP -> panY += 24;
                case GLFW.GLFW_KEY_DOWN -> panY -= 24;
                default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
            }
            positionNodes();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.canvas_usage"));
    }
}
