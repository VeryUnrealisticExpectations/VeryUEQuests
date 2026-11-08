package dev.veryue.vuequests.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.veryue.vuequests.client.widget.QuestDetailsPanel;
import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class QuestDetailsScreen extends Screen {

    private static final int MAX_WIDTH = 320;
    private static final int MARGIN = 12;
    private static final int PADDING = 8;
    private static final int CLOSE_SIZE = 16;

    private final QuestScreen parent;
    private final Quest quest;
    private final Consumer<ClaimQuestRewardPayload> sendRewardClaim;
    private QuestDetailsPanel details;
    private ImageButton closeButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public QuestDetailsScreen(QuestScreen parent, Quest quest, Consumer<ClaimQuestRewardPayload> sendRewardClaim) {
        super(quest.display().title());
        this.parent = parent;
        this.quest = quest;
        this.sendRewardClaim = sendRewardClaim;
    }

    @Override
    protected void init() {
        details = addRenderableWidget(new QuestDetailsPanel(font, quest, this::handleComponentClicked,
                reward -> sendRewardClaim.accept(new ClaimQuestRewardPayload(quest.questId(), reward.getRewardId()))));
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
        parent.render(graphics, -1, -1, partialTick);
        graphics.pose().pushPose();
        // Place the scrim and panel above the parent screen's rendered item icons.
        graphics.pose().translate(0, 0, 200);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blitSprite(QuestWidgetSprites.DETAILS_PANEL, panelX, panelY, panelWidth, panelHeight);
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
