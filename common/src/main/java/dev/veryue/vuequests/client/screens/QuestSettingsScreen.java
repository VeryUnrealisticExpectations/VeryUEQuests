package dev.veryue.vuequests.client.screens;

import net.minecraft.client.renderer.RenderPipelines;

import net.minecraft.client.input.KeyEvent;

import dev.veryue.vuequests.client.widget.LinearQuestSprites;
import dev.veryue.vuequests.client.widget.lists.SettingsGroupList;
import dev.veryue.vuequests.client.widget.panels.SettingsPanel;
import dev.veryue.vuequests.client.widget.panels.TeamSettingsPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.function.Consumer;

public class QuestSettingsScreen extends QuestMenuScreen {

    private final Consumer<CustomPacketPayload> sendToServer;
    private List<SettingsPanel> groups;
    private SettingsGroupList groupList;
    private int selectedGroup;
    private int dividerX;

    public QuestSettingsScreen(QuestMenuNavigation navigation, Consumer<CustomPacketPayload> sendToServer) {
        super(Component.translatable("screen.vuequests.settings"), navigation);
        this.sendToServer = sendToServer;
    }

    @Override
    protected void init() {
        super.init();
        if (groups == null) {
            // Add SettingsPanel instances here in sidebar order, e.g. new ExampleSettingsPanel(font).
            // Each panel supplies its localized title through super(...) and implements rebuildControls().
            // Sidebar labels, selection, layout and scrolling are handled automatically.
            groups = List.of(new TeamSettingsPanel(font, sendToServer));
        }
        groupList = addRenderableWidget(new SettingsGroupList(font,
                groups.stream().map(SettingsPanel::getMessage).toList(), this::selectGroup));
        groups.forEach(this::addRenderableWidget);
        repositionElements();
        groupList.select(selectedGroup);
        setInitialFocus(groupList);
    }

    private void selectGroup(int index) {
        selectedGroup = index;
        for (int group = 0; group < groups.size(); group++) {
            SettingsPanel panel = groups.get(group);
            panel.active = panel.visible = group == selectedGroup;
            if (!panel.active) {
                panel.setFocused(null);
            }
        }
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        if (listener != getFocused()) {
            super.setFocused(listener);
        }
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        dividerX = panelX + panelWidth / 3;
        int padding = Math.clamp(panelWidth / 45, 4, 12);
        groupList.setRectangle(Math.max(1, dividerX - panelX - 2 * padding), Math.max(1, panelHeight - 2 * padding),
                panelX + padding, panelY + padding);
        for (SettingsPanel group : groups) {
            group.setRectangle(Math.max(1, panelX + panelWidth - dividerX - 2 * padding),
                    Math.max(1, panelHeight - 2 * padding), dividerX + padding, panelY + padding);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        SettingsPanel panel = groups.get(selectedGroup);
        if (getFocused() == panel && panel.isEditingText()
                && keyCode != InputConstants.KEY_ESCAPE && keyCode != InputConstants.KEY_TAB) {
            panel.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        LinearQuestSprites.background(graphics, panelX, panelY, panelWidth, panelHeight);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LinearQuestSprites.COLUMN_DIVIDER, dividerX - 1, panelY + 7, 3, Math.max(1, panelHeight - 14));
        LinearQuestSprites.background(graphics, headerX, headerY, headerWidth, HEADER_HEIGHT);
    }
}
