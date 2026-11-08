package dev.veryue.vuequests.client.screens;

import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.widget.QuestCanvas;
import dev.veryue.vuequests.client.widget.QuestSectionPanel;
import dev.veryue.vuequests.client.widget.QuestSectionToggle;
import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestScreen extends Screen {

    private final Consumer<ClaimQuestRewardPayload> sendRewardClaim;
    private QuestSectionPanel sectionPanel;
    private QuestSectionToggle sectionToggle;
    private QuestCanvas canvas;
    private QuestData data;
    private UUID selectedSection;

    public QuestScreen(Consumer<ClaimQuestRewardPayload> sendRewardClaim) {
        super(Component.translatable("screen." + MOD_ID + ".quest_menu"));
        this.sendRewardClaim = sendRewardClaim;
    }

    @Override
    protected void init() {
        sectionPanel = addWidget(new QuestSectionPanel(font, this::selectSection));
        sectionPanel.setTabOrderGroup(0);
        sectionToggle = addWidget(new QuestSectionToggle(font, button -> toggleSections()));
        sectionToggle.setTabOrderGroup(1);
        canvas = addWidget(new QuestCanvas(font, this::openQuest));
        canvas.setTabOrderGroup(2);

        addRenderableOnly(canvas);
        addRenderableOnly(sectionPanel);
        addRenderableOnly(sectionToggle);
        repositionElements();
        refreshData();
    }

    @Override
    protected void repositionElements() {
        int desiredWidth = sectionPanel.isCollapsed() ? QuestSectionPanel.COLLAPSED_WIDTH : Mth.clamp(width / 4, 100, 220);
        int sidebarWidth = Math.min(width / 2, desiredWidth);
        sectionPanel.setRectangle(sidebarWidth, height, 0, 0);
        canvas.setRectangle(width - sidebarWidth, height, sidebarWidth, 0);
        int toggleHeight = Math.max(0, Math.min(height * 2 / 3, height - 2 * QuestWidgetSprites.HEADER_HEIGHT));
        sectionToggle.setRectangle(sectionToggle.getWidth(), toggleHeight, sidebarWidth, (height - toggleHeight) / 2);
        sectionToggle.visible = toggleHeight > 0;
        canvas.setSidebarLayout(sectionPanel.headerWidth() - sidebarWidth, sectionToggle.getRectangle());
    }

    private void toggleSections() {
        sectionPanel.setCollapsed(!sectionPanel.isCollapsed());
        sectionToggle.setCollapsed(sectionPanel.isCollapsed());
        repositionElements();
    }

    private void openQuest(Quest quest) {
        minecraft.setScreen(new QuestDetailsScreen(this, quest, sendRewardClaim));
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
        if (sectionToggle.isMouseOver(mouseX, mouseY)) {
            setTooltipForNextRenderPass(sectionToggle.getTooltip(), DefaultTooltipPositioner.INSTANCE, true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
