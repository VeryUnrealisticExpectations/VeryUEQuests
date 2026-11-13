package dev.veryue.vuequests.client.widget;

import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Matrix3x2fStack;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.QuestCanvasKeyMappings;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Profiler;

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
    private static final double CLICK_SLOP = 3;
    private static final int PAN_STEP = 24;
    private static final SystemToast.SystemToastId QUEST_ID_COPIED = new SystemToast.SystemToastId();

    private final List<QuestNodeWidget> nodes = new ArrayList<>();
    private final List<QuestDependencyLine> dependencyLines = new ArrayList<>();
    private final Consumer<Quest> onOpen;
    private final QuestCanvasKeyMappings keyMappings;
    private Section section;
    private double panX;
    private double panY;
    private double targetPanX;
    private double targetPanY;
    private double renderOffsetX;
    private double renderOffsetY;
    private long lastRenderNanos = System.nanoTime();
    private int zoomLevel = DEFAULT_ZOOM_LEVEL;
    private int headerInset;
    private ScreenRectangle sidebarToggle = ScreenRectangle.empty();
    private boolean panning;
    private QuestNodeWidget pressedNode;
    private double pressX;
    private double pressY;

    public QuestCanvas(Font font, Consumer<Quest> onOpen, QuestCanvasKeyMappings keyMappings) {
        super(font, Component.translatable("screen.vuequests.quest_menu"), QuestWidgetSprites.CANVAS);
        this.onOpen = onOpen;
        this.keyMappings = keyMappings;
    }

    public void setSection(Section section) {
        this.section = section;
        setFocused(null);
        nodes.clear();
        dependencyLines.clear();
        if (section != null) {
            Map<UUID, QuestNodeWidget> nodeIndex = new HashMap<>();
            for (Quest quest : section.questList()) {
                QuestNodeWidget node = new QuestNodeWidget(quest, onOpen);
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

    public void setSidebarLayout(int headerInset, ScreenRectangle sidebarToggle) {
        this.headerInset = headerInset;
        this.sidebarToggle = sidebarToggle;
    }

    @Override
    protected void blitBackground(GuiGraphicsExtractor graphics) {
        // Each translucent sidebar region needs the world background to keep its intended shade.
        int headerBottom = Math.min(contentTop(), getBottom());
        int toggleTop = sidebarToggle.height() > 0 ? sidebarToggle.top() : getBottom();
        int toggleBottom = Math.max(toggleTop, sidebarToggle.bottom());
        blitBackgroundBand(graphics, getX() + headerInset, getY(), headerBottom);
        blitBackgroundBand(graphics, getX(), headerBottom, toggleTop);
        blitBackgroundBand(graphics, sidebarToggle.right(), toggleTop, toggleBottom);
        blitBackgroundBand(graphics, getX(), toggleBottom, getBottom());
    }

    private void blitBackgroundBand(GuiGraphicsExtractor graphics, int left, int top, int bottom) {
        if (left < getRight() && top < bottom) {
            graphics.enableScissor(left, top, getRight(), bottom);
            super.blitBackground(graphics);
            graphics.disableScissor();
        }
    }

    @Override
    protected void renderHeader(GuiGraphicsExtractor graphics) {
        QuestWidgetSprites.renderHeader(graphics, font, getMessage(), getX() + headerInset, getY(), getWidth() - headerInset);
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
        pressedNode = null;
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
        pressedNode = null;
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
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updatePan();
        ScreenRectangle viewport = viewport();
        QuestNodeWidget hovered = panning ? null : nodeAt(mouseX, mouseY);
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        ProfilerFiller profiler = Profiler.get();
        profiler.push("connections");
        try {
            pose.translate((float) renderOffsetX, (float) renderOffsetY);
            int renderedConnections = 0;
            for (QuestDependencyLine line : dependencyLines) {
                if (line.intersects(viewport, renderOffsetX, renderOffsetY)) {
                    line.render(graphics, viewport);
                    renderedConnections++;
                }
            }
            graphics.nextStratum();
            profiler.incrementCounter("total_connections", dependencyLines.size());
            profiler.incrementCounter("rendered_connections", renderedConnections);
            profiler.popPush("nodes");
            int renderedNodes = 0;
            for (QuestNodeWidget node : nodes) {
                if (node.getRight() + renderOffsetX > viewport.left() && node.getX() + renderOffsetX < viewport.right()
                        && node.getBottom() + renderOffsetY > viewport.top() && node.getY() + renderOffsetY < viewport.bottom()) {
                    node.setLocked(!ClientQuestData.prerequisitesMet(node.getQuest()));
                    node.extractRenderState(graphics, node == hovered ? (int) (mouseX - renderOffsetX) : -1,
                            node == hovered ? (int) (mouseY - renderOffsetY) : -1, partialTick);
                    renderedNodes++;
                }
            }
            profiler.incrementCounter("total_nodes", nodes.size());
            profiler.incrementCounter("rendered_nodes", renderedNodes);
        } finally {
            profiler.pop();
            pose.popMatrix();
        }
        if (nodes.isEmpty()) {
            graphics.centeredText(font, Component.translatable("screen.vuequests.no_quests"),
                    viewport.left() + viewport.width() / 2, viewport.top() + viewport.height() / 2, 0xFFAFAFAF);
        }
        graphics.disableScissor();
        if (mouseX >= 0 && mouseY >= 0) {
            graphics.enableScissor(getX(), viewport.bottom(), getRight(), getBottom());
            graphics.text(font, Component.translatable("screen.vuequests.canvas_position",
                            formatCoordinate(toCanvasX(mouseX)), formatCoordinate(toCanvasY(mouseY))),
                    getX() + 4, getBottom() - font.lineHeight - 2, 0xFFE0E0E0);
            graphics.disableScissor();
        }
        if (hovered != null) {
            hovered.renderQuestTooltip(graphics, font, mouseX, mouseY);
        }
    }

    private static String formatCoordinate(double coordinate) {
        return String.format(Locale.ROOT, "%.1f", Math.abs(coordinate) < 0.05 ? 0 : coordinate);
    }

    private QuestNodeWidget nodeAt(double mouseX, double mouseY) {
        if (!isActive() || !viewport().containsPoint((int) mouseX, (int) mouseY)
                || sidebarToggle.containsPoint((int) mouseX, (int) mouseY)) {
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
        if (!panning && focused instanceof QuestNodeWidget node) {
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
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT && !panning && ClientQuestData.isDebugEnabled()
                && (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) && copyQuestIdAt(mouseX, mouseY)) {
            return true;
        }
        if (viewport().containsPoint((int) mouseX, (int) mouseY)
                && handleShortcut(InputConstants.Type.MOUSE.getOrCreate(button))) {
            return true;
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        pressedNode = nodeAt(mouseX, mouseY);
        pressX = mouseX;
        pressY = mouseY;
        panning = true;
        if (pressedNode != null) {
            setFocused(pressedNode);
        }
        return true;
    }

    private boolean copyQuestIdAt(double mouseX, double mouseY) {
        QuestNodeWidget node = nodeAt(mouseX, mouseY);
        if (node == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(node.getQuest().questId().toString());
        SystemToast.addOrUpdate(minecraft.gui.toastManager(), QUEST_ID_COPIED, Component.translatable("screen.vuequests.debug.copied"), null);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && panning && isActive()) {
            if (pressedNode != null) {
                dragX = mouseX - pressX;
                dragY = mouseY - pressY;
                if (Math.hypot(dragX, dragY) < CLICK_SLOP) {
                    return true;
                }
                pressedNode = null;
            }
            targetPanX += dragX;
            targetPanY += dragY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && panning) {
            panning = false;
            QuestNodeWidget clickedNode = pressedNode;
            pressedNode = null;
            if (clickedNode != null && clickedNode == nodeAt(mouseX, mouseY)) {
                clickedNode.playDownSound(Minecraft.getInstance().getSoundManager());
                clickedNode.onPress(event);
            }
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

    public boolean handleShortcut(InputConstants.Key key) {
        if (!isActive()) {
            return false;
        }
        KeyMapping mapping = keyMappings.matching(key);
        if (mapping == keyMappings.recenter) {
            recenter();
        } else if (mapping == keyMappings.zoomIn || mapping == keyMappings.zoomOut) {
            ScreenRectangle viewport = viewport();
            int direction = mapping == keyMappings.zoomIn ? 1 : -1;
            zoomAt(direction, viewport.left() + viewport.width() / 2.0, viewport.top() + viewport.height() / 2.0);
        } else if (mapping == keyMappings.panLeft) {
            targetPanX += PAN_STEP;
        } else if (mapping == keyMappings.panRight) {
            targetPanX -= PAN_STEP;
        } else if (mapping == keyMappings.panUp) {
            targetPanY += PAN_STEP;
        } else if (mapping == keyMappings.panDown) {
            targetPanY -= PAN_STEP;
        } else {
            return false;
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.canvas_usage"));
    }
}
