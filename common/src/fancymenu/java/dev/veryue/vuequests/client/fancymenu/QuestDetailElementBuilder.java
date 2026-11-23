package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.customization.element.ElementBuilder;
import de.keksuccino.fancymenu.customization.element.SerializedElement;
import de.keksuccino.fancymenu.customization.element.editor.AbstractEditorElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import dev.veryue.vuequests.client.screens.LinearQuestPanel;
import dev.veryue.vuequests.client.screens.LinearQuestScreen;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPart;
import net.minecraft.network.chat.Component;

import java.util.List;

public class QuestDetailElementBuilder extends ElementBuilder<QuestDetailElement, QuestDetailEditorElement> {

    private final LinearQuestDetailPart part;
    private final int y;
    private final int height;

    public QuestDetailElementBuilder(LinearQuestDetailPart part, int y, int height) {
        super("vuequests_" + part.id);
        this.part = part;
        this.y = y;
        this.height = height;
    }

    @Override
    public QuestDetailElement buildDefaultInstance() {
        return new QuestDetailElement(this, part, y, height);
    }

    @Override
    public QuestDetailElement deserializeElement(SerializedElement serialized) {
        return buildDefaultInstance();
    }

    @Override
    protected SerializedElement serializeElement(QuestDetailElement element, SerializedElement serialized) {
        return serialized;
    }

    @Override
    public QuestDetailEditorElement wrapIntoEditorElement(QuestDetailElement element, LayoutEditorScreen editor) {
        return new QuestDetailEditorElement(element, editor);
    }

    @Override
    public Component getDisplayName(AbstractElement element) {
        return name(part);
    }

    static Component name(LinearQuestDetailPart part) {
        return Component.translatable("fancymenu.vuequests." + part.id);
    }

    @Override
    public Component[] getDescription(AbstractElement element) {
        return new Component[] {Component.translatable("fancymenu.vuequests.detail_part.description")};
    }

    @Override
    public boolean shouldShowUpInEditorElementMenu(LayoutEditorScreen editor) {
        return editor.layoutTargetScreen instanceof LinearQuestScreen && findEditorParent(editor) != null;
    }

    static QuestPanelElement findEditorParent(LayoutEditorScreen editor) {
        List<AbstractEditorElement<?, ?>> elements = editor.getAllElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i).element instanceof QuestPanelElement candidate && candidate.panel() == LinearQuestPanel.QUEST_DETAILS) {
                return candidate;
            }
        }
        return null;
    }
}
