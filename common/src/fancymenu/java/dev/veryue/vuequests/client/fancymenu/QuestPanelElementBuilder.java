package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.customization.element.ElementBuilder;
import de.keksuccino.fancymenu.customization.element.SerializedElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import dev.veryue.vuequests.client.screens.LinearQuestPanel;
import dev.veryue.vuequests.client.screens.LinearQuestScreen;
import net.minecraft.network.chat.Component;

public class QuestPanelElementBuilder extends ElementBuilder<QuestPanelElement, QuestPanelEditorElement> {

    private final LinearQuestPanel panel;
    private final int width;
    private final int height;
    private final String translationKey;

    public QuestPanelElementBuilder(String id, LinearQuestPanel panel, int width, int height) {
        super("vuequests_" + id);
        this.panel = panel;
        this.width = width;
        this.height = height;
        translationKey = "fancymenu.vuequests." + id;
    }

    @Override
    public QuestPanelElement buildDefaultInstance() {
        return new QuestPanelElement(this, panel, width, height);
    }

    @Override
    public QuestPanelElement deserializeElement(SerializedElement serialized) {
        return buildDefaultInstance();
    }

    @Override
    protected SerializedElement serializeElement(QuestPanelElement element, SerializedElement serialized) {
        return serialized;
    }

    @Override
    public QuestPanelEditorElement wrapIntoEditorElement(QuestPanelElement element, LayoutEditorScreen editor) {
        return new QuestPanelEditorElement(element, editor);
    }

    @Override
    public Component getDisplayName(AbstractElement element) {
        return Component.translatable(translationKey);
    }

    @Override
    public Component[] getDescription(AbstractElement element) {
        if (panel == LinearQuestPanel.BACKDROP) {
            return new Component[] {Component.translatable("fancymenu.vuequests.backdrop.description")};
        }
        return new Component[] {Component.translatable("fancymenu.vuequests.panel.description")};
    }

    @Override
    public boolean shouldShowUpInEditorElementMenu(LayoutEditorScreen editor) {
        return editor.layoutTargetScreen instanceof LinearQuestScreen;
    }
}
