package dev.veryue.vuequests.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.QuestMenuActions;
import dev.veryue.vuequests.client.util.GuiSpriteRenderer;
import dev.veryue.vuequests.client.widget.QuestDependencyTooltip;
import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
import dev.veryue.vuequests.client.widget.panels.QuestDetailsPanel;
import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static dev.veryue.vuequests.client.widget.QuestWidgetSprites.DEPENDENCY_LOCK_SIZE;

public class QuestDetailsScreen extends Screen {

    private static final int MAX_WIDTH = 320;
    private static final int MARGIN = 12;
    private static final int PADDING = 8;
    private static final int CLOSE_SIZE = 16;

    private final GraphQuestScreen parent;
    private Quest quest;
    private final QuestMenuActions questActions;
    private QuestDependencyTooltip dependencies;
    private QuestDetailsPanel details;
    private ImageButton closeButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public QuestDetailsScreen(GraphQuestScreen parent, Quest quest, QuestMenuActions questActions) {
        super(quest.display().title());
        this.parent = parent;
        this.quest = quest;
        this.questActions = questActions;
    }

    @Override
    public Component getTitle() {
        return quest.display().title();
    }

    @Override
    protected void init() {
        dependencies = new QuestDependencyTooltip(quest);
        details = addRenderableWidget(new QuestDetailsPanel(font, quest, this::handleComponentClicked,
                reward -> questActions.claimReward().accept(quest.questId(), reward.getRewardId()),
                objective -> questActions.interactWithObjective().accept(quest.questId(), objective.getObjectiveId())));
        Component close = Component.translatable("screen.vuequests.close");
        WidgetSprites closeSprites = new WidgetSprites(QuestWidgetSprites.CLOSE, QuestWidgetSprites.CLOSE);
        closeButton = addRenderableWidget(new ImageButton(0, 0, CLOSE_SIZE, CLOSE_SIZE, closeSprites, button -> onClose(), close));
        closeButton.setTooltip(Tooltip.create(close));
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (parent.width != width || parent.height != height) {
            parent.resize(minecraft, width, height);
        }
        panelWidth = Math.max(1, Math.min(MAX_WIDTH, width - 2 * MARGIN));
        int controlsWidth = CLOSE_SIZE + details.scrollbarWidth();
        if (!dependencies.isEmpty()) {
            controlsWidth += DEPENDENCY_LOCK_SIZE;
        }
        int bodyWidth = Math.max(1, panelWidth - 2 * PADDING - controlsWidth);
        int bodyHeight = Math.max(1, height - 2 * (MARGIN + PADDING));
        details.fit(bodyWidth, bodyHeight, controlsWidth / 2);
        panelHeight = details.getHeight() + 2 * PADDING;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        details.setPosition(panelX + PADDING, panelY + PADDING);
        closeButton.setPosition(panelX + panelWidth - PADDING - CLOSE_SIZE, panelY + PADDING);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Quest latest = ClientQuestData.getTranslatedData().quests().get(quest.questId());
        if (latest == null) {
            onClose();
            return;
        }
        if (latest != quest) {
            quest = latest;
            dependencies = new QuestDependencyTooltip(latest);
            details.setQuest(latest);
            repositionElements();
        }
        parent.render(graphics, -1, -1, partialTick);
        graphics.pose().pushPose();
        // Place the scrim and panel above the parent screen's rendered item icons.
        graphics.pose().translate(0, 0, 200);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
            if (!dependencies.isEmpty()) {
                int lockX = closeButton.getX() - DEPENDENCY_LOCK_SIZE;
                int lockY = closeButton.getY();
                boolean highlighted = mouseX >= lockX && mouseX < lockX + DEPENDENCY_LOCK_SIZE
                        && mouseY >= lockY && mouseY < lockY + DEPENDENCY_LOCK_SIZE;
                QuestWidgetSprites.blitDependencyLock(graphics, lockX, lockY, highlighted);
                if (highlighted) {
                    dependencies.render(graphics, font, mouseX, mouseY);
                }
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiSpriteRenderer.blit(graphics, QuestWidgetSprites.DETAILS_PANEL, panelX, panelY, panelWidth, panelHeight, 0xFFFFFFFF);
        RenderSystem.disableBlend();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
