package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class QuestCanvas extends QuestPanel {

    private static final double[] ZOOM_LEVELS = {0.25, 0.5, 0.75, 1, 1.5, 2};
    private static final int DEFAULT_ZOOM_LEVEL = 2;
    private static final double PAN_SMOOTHING_NANOS = 25_000_000.0;
    private static final double PAN_SNAP_DISTANCE = 0.01;

    private final List<QuestNodeWidget> nodes = new ArrayList<>();
    private final List<QuestDependencyLine> dependencyLines = new ArrayList<>();
    private final Consumer<Quest> onPreview;
    private Section section;
    private double panX;
    private double panY;
    private double targetPanX;
    private double targetPanY;
    private double renderOffsetX;
    private double renderOffsetY;
    private long lastRenderNanos = System.nanoTime();
    private int zoomLevel = DEFAULT_ZOOM_LEVEL;
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
        setMessage(section == null ? Component.translatable("screen.vuequests.quest_menu") : section.name());
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

    public double getZoom() {
        return ZOOM_LEVELS[zoomLevel];
    }

    public double toCanvasX(double screenX) {
        ScreenRectangle viewport = viewport();
        return (screenX - viewport.left() - viewport.width() / 2.0 - panX) / getZoom();
    }

    public double toCanvasY(double screenY) {
        ScreenRectangle viewport = viewport();
        return (screenY - viewport.top() - viewport.height() / 2.0 - panY) / getZoom();
    }

    private void recenter() {
        panX = 0;
        panY = 0;
        targetPanX = 0;
        targetPanY = 0;
        zoomLevel = DEFAULT_ZOOM_LEVEL;
        panning = false;
        positionNodes();
    }

    private void positionNodes() {
        ScreenRectangle viewport = viewport();
        double zoom = getZoom();
        int size = (int) Math.round(QuestNodeWidget.SIZE * zoom);
        double layoutPanX = Math.ceil(panX);
        double layoutPanY = Math.ceil(panY);
        renderOffsetX = panX - layoutPanX;
        renderOffsetY = panY - layoutPanY;
        double originX = viewport.left() + viewport.width() / 2.0 + layoutPanX - size / 2.0;
        double originY = viewport.top() + viewport.height() / 2.0 + layoutPanY - size / 2.0;
        for (QuestNodeWidget node : nodes) {
            int x = (int) Math.round(originX + node.getQuest().position().x() * zoom);
            int y = (int) Math.round(originY + node.getQuest().position().y() * zoom);
            node.setRectangle(size, size, x, y);
        }
    }

    private void updatePan() {
        long now = System.nanoTime();
        long elapsed = now - lastRenderNanos;
        lastRenderNanos = now;
        if (panX == targetPanX && panY == targetPanY) {
            return;
        }
        double amount = -Math.expm1(-elapsed / PAN_SMOOTHING_NANOS);
        panX = interpolatePan(panX, targetPanX, amount);
        panY = interpolatePan(panY, targetPanY, amount);
        positionNodes();
    }

    private static double interpolatePan(double pan, double target, double amount) {
        double next = Mth.lerp(amount, pan, target);
        return Math.abs(target - next) < PAN_SNAP_DISTANCE ? target : next;
    }

    private void zoomAt(int direction, double mouseX, double mouseY) {
        int nextLevel = Mth.clamp(zoomLevel + direction, 0, ZOOM_LEVELS.length - 1);
        if (nextLevel == zoomLevel) {
            return;
        }
        double x = toCanvasX(mouseX);
        double y = toCanvasY(mouseY);
        double previousZoom = getZoom();
        zoomLevel = nextLevel;
        double zoomChange = previousZoom - getZoom();
        double shiftX = x * zoomChange;
        double shiftY = y * zoomChange;
        panX += shiftX;
        panY += shiftY;
        targetPanX += shiftX;
        targetPanY += shiftY;
        positionNodes();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePan();
        ScreenRectangle viewport = viewport();
        Quest hovered = questAt(mouseX, mouseY);
        if (hovered != null) {
            onPreview.accept(hovered);
        }
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(renderOffsetX, renderOffsetY, 0);
            for (QuestDependencyLine line : dependencyLines) {
                line.render(graphics);
            }
            graphics.flush();
            boolean hoverAllowed = !panning && viewport.containsPoint(mouseX, mouseY);
            for (QuestNodeWidget node : nodes) {
                if (node.getRight() + renderOffsetX > viewport.left() && node.getX() + renderOffsetX < viewport.right()
                        && node.getBottom() + renderOffsetY > viewport.top() && node.getY() + renderOffsetY < viewport.bottom()) {
                    node.render(graphics, hoverAllowed ? mouseX : -1, hoverAllowed ? mouseY : -1, partialTick);
                }
            }
        } finally {
            pose.popPose();
        }
        if (nodes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.vuequests.no_quests"),
                    viewport.left() + viewport.width() / 2, viewport.top() + viewport.height() / 2, 0xFFAFAFAF);
        }
        graphics.disableScissor();
        graphics.enableScissor(getX(), viewport.bottom(), getRight(), getBottom());
        graphics.drawString(font, Component.translatable("screen.vuequests.canvas_position",
                        formatCoordinate(toCanvasX(mouseX)), formatCoordinate(toCanvasY(mouseY))),
                getX() + 4, getBottom() - font.lineHeight - 2, 0xFFE0E0E0);
        graphics.disableScissor();
    }

    private static String formatCoordinate(double coordinate) {
        return String.format(Locale.ROOT, "%.1f", Math.abs(coordinate) < 0.05 ? 0 : coordinate);
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
            if (node.isMouseOver(mouseX - renderOffsetX, mouseY - renderOffsetY)) {
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
                if (node.getX() + renderOffsetX < viewport.left() || node.getRight() + renderOffsetX > viewport.right()) {
                    panX += viewport.left() + viewport.width() / 2.0 - node.getX() - renderOffsetX - node.getWidth() / 2.0;
                    targetPanX = panX;
                }
                if (node.getY() + renderOffsetY < viewport.top() || node.getBottom() + renderOffsetY > viewport.bottom()) {
                    panY += viewport.top() + viewport.height() / 2.0 - node.getY() - renderOffsetY - node.getHeight() / 2.0;
                    targetPanY = panY;
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
            targetPanX += dragX;
            targetPanY += dragY;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isActive() || !viewport().containsPoint((int) mouseX, (int) mouseY) || scrollY == 0) {
            return false;
        }
        zoomAt(scrollY > 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive() || !isFocused()) {
            return false;
        }
        // todo; add configurable keybinds that are context tied to the UI.
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            recenter();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD
                || keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            ScreenRectangle viewport = viewport();
            int direction = keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD ? 1 : -1;
            zoomAt(direction, viewport.left() + viewport.width() / 2.0, viewport.top() + viewport.height() / 2.0);
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> targetPanX += 24;
                case GLFW.GLFW_KEY_RIGHT -> targetPanX -= 24;
                case GLFW.GLFW_KEY_UP -> targetPanY += 24;
                case GLFW.GLFW_KEY_DOWN -> targetPanY -= 24;
                default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
            }
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
