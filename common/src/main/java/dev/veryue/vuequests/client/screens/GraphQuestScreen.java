package dev.veryue.vuequests.client.screens;

import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.QuestCanvasKeyMappings;
import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
import dev.veryue.vuequests.client.widget.controls.QuestSectionToggle;
import dev.veryue.vuequests.client.widget.panels.QuestCanvas;
import dev.veryue.vuequests.client.widget.panels.QuestSectionPanel;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Profiler;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class GraphQuestScreen extends QuestMenuScreen {

    private final Consumer<CustomPacketPayload> sendToServer;
    private final QuestCanvasKeyMappings canvasKeys;
    private QuestSectionPanel sectionPanel;
    private QuestSectionToggle sectionToggle;
    private QuestCanvas canvas;
    private QuestData data;
    private UUID selectedSection;

    public GraphQuestScreen(Consumer<CustomPacketPayload> sendToServer, QuestCanvasKeyMappings canvasKeys,
                            QuestMenuNavigation navigation) {
        super(Component.translatable("screen." + MOD_ID + ".quest_menu"), navigation);
        this.sendToServer = sendToServer;
        this.canvasKeys = canvasKeys;
    }

    @Override
    protected void init() {
        super.init();
        sectionPanel = addWidget(new QuestSectionPanel(font, this::selectSection));
        sectionPanel.setTabOrderGroup(0);
        sectionToggle = addWidget(new QuestSectionToggle(font, button -> toggleSections()));
        sectionToggle.setTabOrderGroup(1);
        canvas = addWidget(new QuestCanvas(font, this::openQuest, canvasKeys));
        canvas.setTabOrderGroup(2);

        addRenderableOnly(canvas);
        addRenderableOnly(sectionPanel);
        addRenderableOnly(sectionToggle);
        repositionElements();
        refreshData();
    }

    @Override
    protected void repositionElements() {
        positionHeader(0, 0, Math.max(1, width));
        int contentY = HEADER_HEIGHT;
        int contentHeight = Math.max(1, height - contentY);
        panelX = 0;
        panelY = contentY;
        panelWidth = width;
        panelHeight = contentHeight;
        int desiredWidth = sectionPanel.isCollapsed() ? QuestSectionPanel.COLLAPSED_WIDTH : Mth.clamp(width / 4, 100, 220);
        int sidebarWidth = Math.min(width / 2, desiredWidth);
        sectionPanel.setRectangle(sidebarWidth, contentHeight, 0, contentY);
        canvas.setRectangle(width - sidebarWidth, contentHeight, sidebarWidth, contentY);
        int toggleHeight = Math.max(0, Math.min(contentHeight * 2 / 3, contentHeight - 2 * QuestWidgetSprites.HEADER_HEIGHT));
        sectionToggle.setRectangle(sectionToggle.getWidth(), toggleHeight, sidebarWidth, contentY + (contentHeight - toggleHeight) / 2);
        sectionToggle.visible = toggleHeight > 0;
        canvas.setSidebarLayout(sectionPanel.headerWidth() - sidebarWidth, sectionToggle.getRectangle());
    }

    private void toggleSections() {
        sectionPanel.setCollapsed(!sectionPanel.isCollapsed());
        sectionToggle.setCollapsed(sectionPanel.isCollapsed());
        repositionElements();
    }

    private void openQuest(Quest quest) {
        minecraft.gui.setScreen(new QuestDetailsScreen(this, quest, sendToServer));
    }

    private void refreshData() {
        QuestData latest = ClientQuestData.getData();
        if (latest == data) {
            return;
        }
        data = latest;
        List<Section> sections = data.sections().values().stream()
                .sorted(Comparator.comparing((Section section) -> section.name().getString(), String.CASE_INSENSITIVE_ORDER).thenComparing(Section::sectionUUID))
                .toList();
        sectionPanel.setSections(sections);
        Section selected = selectedSection == null ? null : data.sections().get(selectedSection);
        selectSection(selected == null && !sections.isEmpty() ? sections.getFirst() : selected);
    }

    private void selectSection(Section section) {
        UUID sectionId = section == null ? null : section.sectionUUID();
        sectionPanel.setSelected(sectionId);
        if (sectionId != null && sectionId.equals(selectedSection) && canvas.getSection() == section) {
            return;
        }
        selectedSection = sectionId;
        canvas.setSection(section);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        return handleMenuShortcut(InputConstants.getKey(event))
                || canvas.handleShortcut(InputConstants.getKey(event))
                || super.keyPressed(event);
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        // Vanilla clears focus even on the same panel, interrupting an active mouse drag.
        if (listener == getFocused()) {
            if (listener != null) {
                listener.setFocused(true);
            }
            return;
        }
        super.setFocused(listener);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("vuequests_graph");
        profiler.push("refresh");
        try {
            refreshData();
            profiler.popPush("widgets");
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        } finally {
            profiler.pop();
            profiler.pop();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("vanilla_background");
        try {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            profiler.popPush("header_background");
            QuestWidgetSprites.blit(graphics, QuestWidgetSprites.CANVAS, headerX, headerY, headerWidth, HEADER_HEIGHT);
        } finally {
            profiler.pop();
        }
    }
}
