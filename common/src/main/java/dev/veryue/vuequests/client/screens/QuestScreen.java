package dev.veryue.vuequests.client.screens;

import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.widget.QuestCanvas;
import dev.veryue.vuequests.client.widget.QuestPreviewPanel;
import dev.veryue.vuequests.client.widget.QuestSectionPanel;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestScreen extends Screen {

    private QuestSectionPanel sectionPanel;
    private QuestPreviewPanel previewPanel;
    private QuestCanvas canvas;
    private QuestData data;
    private UUID selectedSection;

    public QuestScreen() {
        super(Component.translatable("screen." + MOD_ID + ".quest_menu"));
    }

    @Override
    protected void init() {
        clearFocus();
        clearWidgets();
        int sidebarWidth = Mth.clamp(width / 4, 100, 220);
        int sectionHeight = height * 7 / 10;

        sectionPanel = addWidget(new QuestSectionPanel(font, this::selectSection));
        sectionPanel.setRectangle(sidebarWidth, sectionHeight, 0, 0);
        sectionPanel.setTabOrderGroup(0);
        previewPanel = addWidget(new QuestPreviewPanel(font));
        previewPanel.setRectangle(sidebarWidth, height - sectionHeight, 0, sectionHeight);
        previewPanel.setTabOrderGroup(1);
        canvas = addWidget(new QuestCanvas(font, previewPanel::setQuest));
        canvas.setRectangle(width - sidebarWidth, height, sidebarWidth, 0);
        canvas.setTabOrderGroup(2);

        addRenderableOnly(sectionPanel);
        addRenderableOnly(canvas);
        addRenderableOnly(previewPanel);
        data = null;
        refreshData();
    }

    private void refreshData() {
        QuestData latest = ClientQuestData.getData();
        if (latest == data) {
            return;
        }
        data = latest;
        List<Section> sections = data.sections().values().stream()
                .sorted(Comparator.comparing(Section::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Section::sectionUUID))
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
        previewPanel.setQuest(null);
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshData();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
