package dev.veryue.vuequests.client.screens;

import net.minecraft.client.renderer.RenderPipelines;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.widget.LinearQuestDetails;
import dev.veryue.vuequests.client.widget.LinearQuestList;
import dev.veryue.vuequests.client.widget.LinearQuestSprites;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Profiler;

import java.util.function.Consumer;

public class LinearQuestScreen extends Screen {

    private final Consumer<CustomPacketPayload> sendToServer;
    private LinearQuestList questList;
    private LinearQuestDetails details;
    private QuestData data;
    private PlayerUser user;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int dividerX;

    public LinearQuestScreen(Consumer<CustomPacketPayload> sendToServer) {
        super(Component.translatable("screen.vuequests.quest_menu"));
        this.sendToServer = sendToServer;
    }

    @Override
    protected void init() {
        if (details == null) {
            details = new LinearQuestDetails(font, this::handleTextClick,
                    reward -> sendToServer.accept(new ClaimQuestRewardPayload(details.getQuest().questId(), reward.getRewardId())),
                    objective -> sendToServer.accept(new InteractQuestObjectivePayload(details.getQuest().questId(), objective.getObjectiveId())));
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
        panelWidth = Math.max(1, Math.min(960, width * 4 / 5));
        panelHeight = Math.max(1, Math.min(540, height * 4 / 5));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        dividerX = panelX + panelWidth / 3;
        int padding = Math.min(12, Math.max(4, panelWidth / 45));
        questList.setRectangle(Math.max(1, dividerX - panelX - 2 * padding), Math.max(1, panelHeight - 2 * padding),
                panelX + padding, panelY + padding);
        details.setPosition(dividerX + padding, panelY + padding);
        details.fit(Math.max(1, panelX + panelWidth - padding - details.getX()), Math.max(1, panelHeight - 2 * padding));
    }

    private void refreshData() {
        QuestData latest = ClientQuestData.getData();
        PlayerUser latestUser = ClientQuestData.getUser();
        if (latest == data && latestUser == user) return;
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
        ProfilerFiller profiler = Profiler.get();
        profiler.push("vanilla_background");
        try {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            profiler.popPush("panel_background");
            LinearQuestSprites.background(graphics, panelX, panelY, panelWidth, panelHeight);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LinearQuestSprites.COLUMN_DIVIDER, dividerX - 1, panelY + 7, 3, Math.max(1, panelHeight - 14));
        } finally {
            profiler.pop();
        }
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
