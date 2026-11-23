package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.customization.element.editor.AbstractEditorElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;

public class QuestPanelEditorElement extends AbstractEditorElement<QuestPanelEditorElement, QuestPanelElement> {

    public QuestPanelEditorElement(QuestPanelElement element, LayoutEditorScreen editor) {
        super(element, editor);
    }

    @Override
    public void init() {
        super.init();
        if (element.style != null) {
            element.style.buildContextMenuEntryAndAddTo(rightClickMenu, this);
        }
    }
}
