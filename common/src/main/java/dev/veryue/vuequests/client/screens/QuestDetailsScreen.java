package dev.veryue.vuequests.client.screens;

import dev.veryue.vuequests.client.util.GuiSpriteRenderer;
import dev.veryue.vuequests.client.widget.QuestDetailsPanel;
import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.function.Consumer;

public class QuestDetailsScreen extends Screen {

    private static final int MAX_WIDTH = 320;
    private static final int MARGIN = 12;
    private static final int PADDING = 8;
    private static final int CLOSE_SIZE = 16;

    private final QuestScreen parent;
    private final Quest quest;
    private final Consumer<CustomPacketPayload> sendToServer;
    private QuestDetailsPanel details;
    private ImageButton closeButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public QuestDetailsScreen(QuestScreen parent, Quest quest, Consumer<CustomPacketPayload> sendToServer) {
        super(quest.display().title());
        this.parent = parent;
        this.quest = quest;
        this.sendToServer = sendToServer;
    }

    @Override
    protected void init() {
        details = addRenderableWidget(new QuestDetailsPanel(font, quest, this::handleTextClick,
                reward -> sendToServer.accept(new ClaimQuestRewardPayload(quest.questId(), reward.getRewardId())),
                objective -> sendToServer.accept(new InteractQuestObjectivePayload(quest.questId(), objective.getObjectiveId()))));
        Component close = Component.translatable("screen.vuequests.close");
        WidgetSprites closeSprites = new WidgetSprites(QuestWidgetSprites.CLOSE, QuestWidgetSprites.CLOSE);
        closeButton = addRenderableWidget(new ImageButton(0, 0, CLOSE_SIZE, CLOSE_SIZE, closeSprites, button -> onClose(), close));
        closeButton.setTooltip(Tooltip.create(close));
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (parent.width != width || parent.height != height) {
            parent.resize(width, height);
        }
        panelWidth = Math.max(1, Math.min(MAX_WIDTH, width - 2 * MARGIN));
        int controlsWidth = CLOSE_SIZE + details.scrollbarWidth();
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        parent.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        parent.extractRenderState(graphics, -1, -1, partialTick);
        graphics.nextStratum();
        graphics.fill(0, 0, width, height, 0x99000000);
        GuiSpriteRenderer.blit(graphics, QuestWidgetSprites.DETAILS_PANEL, panelX, panelY, panelWidth, panelHeight, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean handleTextClick(Style style) {
        if (style.getClickEvent() == null) return false;
        defaultHandleGameClickEvent(style.getClickEvent(), minecraft, this);
        return true;
    }
}
