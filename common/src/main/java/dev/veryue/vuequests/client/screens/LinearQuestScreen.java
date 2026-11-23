package dev.veryue.vuequests.client.screens;

import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.QuestMenuActions;
import dev.veryue.vuequests.client.widget.LinearQuestSprites;
import dev.veryue.vuequests.client.widget.lists.LinearQuestList;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetails;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.PlayerUser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class LinearQuestScreen extends QuestMenuScreen {

    private final QuestMenuActions questActions;
    private LinearQuestLayout layout = LinearQuestLayout.DEFAULT;
    private LinearQuestList questList;
    private LinearQuestDetails details;
    private QuestData data;
    private PlayerUser user;
    private int dividerX;

    public LinearQuestScreen(QuestMenuActions questActions, QuestMenuNavigation navigation) {
        super(Component.translatable("screen.vuequests.quest_menu"), navigation);
        this.questActions = questActions;
    }

    @Override
    protected void init() {
        super.init();
        if (details == null) {
            details = new LinearQuestDetails(font, this::handleTextClick,
                    reward -> questActions.claimReward().accept(details.getQuest().questId(), reward.getRewardId()),
                    objective -> questActions.interactWithObjective().accept(details.getQuest().questId(), objective.getObjectiveId()));
            questList = new LinearQuestList(font, details::setQuest);
        }
        addRenderableWidget(questList);
        addRenderableWidget(details);
        repositionElements();
        refreshData();
        setInitialFocus(questList);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        dividerX = panelX + panelWidth / 3;
        layout = LinearQuestCustomization.getLayout(this);
        positionPanels();
    }

    public boolean isUsablePlacement(LinearQuestPanel panel, ScreenRectangle bounds) {
        int minimumWidth = panel == LinearQuestPanel.NAVIGATION ? minimumHeaderWidth() : 1;
        int minimumHeight = panel == LinearQuestPanel.NAVIGATION ? HEADER_HEIGHT : 1;
        return bounds.width() >= minimumWidth && bounds.height() >= minimumHeight
                && bounds.left() >= 0 && bounds.top() >= 0
                && bounds.width() <= width - bounds.left() && bounds.height() <= height - bounds.top();
    }

    private void refreshLayout() {
        LinearQuestLayout latest = LinearQuestCustomization.getLayout(this);
        if (!latest.equals(layout)) {
            layout = latest;
            positionPanels();
        }
    }

    private void positionPanels() {
        ScreenRectangle navigationBounds = layout.navigation();
        if (navigationBounds == null) {
            positionHeader(panelX, panelY - HEADER_HEIGHT - HEADER_GAP, panelWidth);
        } else {
            positionHeader(navigationBounds.left(), navigationBounds.top(), navigationBounds.width(), navigationBounds.height(),
                    layout.navigationChevrons());
        }

        int padding = Math.min(12, Math.max(4, panelWidth / 45));
        int bodyHeight = Math.max(1, panelHeight - 2 * padding);
        ScreenRectangle listBounds = layout.questList();
        if (listBounds == null) {
            listBounds = new ScreenRectangle(panelX + padding, panelY + padding,
                    Math.max(1, dividerX - panelX - 2 * padding), bodyHeight);
        }
        questList.setRectangle(listBounds.width(), listBounds.height(), listBounds.left(), listBounds.top());
        questList.setCompactRows(layout.compactRows());

        ScreenRectangle detailsBounds = layout.questDetails();
        if (detailsBounds == null) {
            detailsBounds = new ScreenRectangle(dividerX + padding, panelY + padding,
                    Math.max(1, panelX + panelWidth - dividerX - 2 * padding), bodyHeight);
        }
        details.setPosition(detailsBounds.left(), detailsBounds.top());
        details.setLayout(detailsBounds.width(), detailsBounds.height(), layout.detailParts());
    }

    private void refreshData() {
        QuestData latest = ClientQuestData.getData();
        PlayerUser latestUser = ClientQuestData.getUser();
        if (latest == data && latestUser == user) {
            return;
        }
        data = latest;
        user = latestUser;
        questList.setQuests(data.quests().values().stream().toList());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("vuequests_linear");
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
        refreshLayout();
        ProfilerFiller profiler = Profiler.get();
        profiler.push("vanilla_background");
        try {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            if (!layout.hasPlacements()) {
                profiler.popPush("panel_background");
                LinearQuestSprites.background(graphics, panelX, panelY, panelWidth, panelHeight);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LinearQuestSprites.COLUMN_DIVIDER, dividerX - 1, panelY + 7, 3, Math.max(1, panelHeight - 14));
                LinearQuestSprites.background(graphics, headerX, headerY, headerWidth, HEADER_HEIGHT);
            }
        } finally {
            profiler.pop();
        }
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
        if (!layout.clearWorld()) {
            super.extractBlurredBackground(graphics);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        refreshLayout();
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        refreshLayout();
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        refreshLayout();
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        refreshLayout();
        return super.keyPressed(event);
    }

    private boolean handleTextClick(Style style) {
        if (style.getClickEvent() == null) {
            return false;
        }
        defaultHandleGameClickEvent(style.getClickEvent(), minecraft, this);
        return true;
    }
}
