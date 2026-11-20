package dev.veryue.vuequests.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.client.QuestMenuKeyMappings;
import dev.veryue.vuequests.client.widget.controls.QuestMenuKeyButton;
import dev.veryue.vuequests.client.widget.controls.QuestMenuTabButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class QuestMenuScreen extends Screen {

    protected static final int HEADER_HEIGHT = 24;
    protected static final int HEADER_GAP = 6;
    private static final int HEADER_PADDING = 7;

    protected final QuestMenuNavigation navigation;
    protected final QuestMenuKeyMappings menuKeys;
    protected int panelX;
    protected int panelY;
    protected int panelWidth;
    protected int panelHeight;
    protected int headerX;
    protected int headerY;
    protected int headerWidth;

    private final List<QuestMenuTabButton> tabs = new ArrayList<>();
    private QuestMenuKeyButton previousButton;
    private QuestMenuKeyButton nextButton;

    protected QuestMenuScreen(Component title, QuestMenuNavigation navigation) {
        super(title);
        this.navigation = navigation;
        menuKeys = navigation.getKeyMappings();
    }

    @Override
    protected void init() {
        previousButton = addRenderableWidget(new QuestMenuKeyButton(font, menuKeys.previous,
                button -> selectPage(navigation.getSelectedIndex() - 1)));
        tabs.clear();
        List<QuestMenuPage> pages = navigation.getPages();
        for (int index = 0; index < pages.size(); index++) {
            int pageIndex = index;
            tabs.add(addRenderableWidget(new QuestMenuTabButton(font, pages.get(index).getTitle(),
                    () -> navigation.getSelectedIndex() == pageIndex, button -> selectPage(pageIndex))));
        }
        nextButton = addRenderableWidget(new QuestMenuKeyButton(font, menuKeys.next,
                button -> selectPage(navigation.getSelectedIndex() + 1)));
        previousButton.active = nextButton.active = pages.size() > 1;
    }

    @Override
    protected void repositionElements() {
        panelWidth = Math.clamp(width * 4 / 5, 1, 960);
        panelHeight = Math.max(1, Math.min(Math.min(540, height * 4 / 5), height - HEADER_HEIGHT - HEADER_GAP - 12));
        panelX = (width - panelWidth) / 2;
        int headerY = (height - panelHeight - HEADER_HEIGHT - HEADER_GAP) / 2;
        panelY = headerY + HEADER_HEIGHT + HEADER_GAP;
        positionHeader(panelX, headerY, panelWidth);
    }

    protected void positionHeader(int x, int y, int width) {
        headerX = x;
        headerY = y;
        headerWidth = width;
        int keySize = QuestMenuKeyButton.SIZE;
        int keyY = y + (HEADER_HEIGHT - keySize) / 2;
        previousButton.setRectangle(keySize, keySize, x + HEADER_PADDING, keyY);
        nextButton.setRectangle(keySize, keySize, x + width - HEADER_PADDING - keySize, keyY);
        int tabsX = x + keySize + 2 * HEADER_PADDING;
        int tabsWidth = Math.max(1, width - 2 * (keySize + 2 * HEADER_PADDING));
        for (int index = 0; index < tabs.size(); index++) {
            int start = tabsX + tabsWidth * index / tabs.size();
            int end = tabsX + tabsWidth * (index + 1) / tabs.size();
            tabs.get(index).setRectangle(end - start, HEADER_HEIGHT, start, y);
        }
    }

    private boolean selectPage(int index) {
        QuestMenuScreen screen = navigation.select(index);
        if (screen == this) {
            return false;
        }
        minecraft.setScreen(screen);
        return minecraft.screen == screen;
    }

    protected boolean handleMenuShortcut(InputConstants.Key key) {
        KeyMapping mapping = menuKeys.matching(key);
        if (mapping == menuKeys.previous) {
            if (selectPage(navigation.getSelectedIndex() - 1)) {
                previousButton.playDownSound(minecraft.getSoundManager());
            }
            return true;
        }
        if (mapping == menuKeys.next) {
            if (selectPage(navigation.getSelectedIndex() + 1)) {
                nextButton.playDownSound(minecraft.getSoundManager());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return handleMenuShortcut(InputConstants.getKey(keyCode, scanCode))
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= headerX && mouseX < headerX + headerWidth
                && mouseY >= headerY && mouseY < headerY + HEADER_HEIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return handleMenuShortcut(InputConstants.Type.MOUSE.getOrCreate(button)) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
