package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.customization.element.editor.AbstractEditorElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import de.keksuccino.fancymenu.util.properties.Property;
import de.keksuccino.fancymenu.util.properties.Property.BooleanProperty;
import de.keksuccino.fancymenu.util.rendering.ui.contextmenu.v2.ContextMenu;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPart;
import dev.veryue.vuequests.client.widget.panels.LinearQuestTextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

public class QuestDetailEditorElement extends AbstractEditorElement<QuestDetailEditorElement, QuestDetailElement> {

    public QuestDetailEditorElement(QuestDetailElement element, LayoutEditorScreen editor) {
        super(element, editor);
        element.editorParent = () -> QuestDetailElementBuilder.findEditorParent(editor);
        settings.setAnchorPointChangeable(false);
    }

    @Override
    public void init() {
        super.init();
        topLeftDisplay.removeLine("anchor_point");
        topLeftDisplay.addLine("vuequests_parent", () -> Component.translatable("fancymenu.vuequests.detail_part.parent"));
        ContextMenu options = new ContextMenu();
        rightClickMenu.addSubMenuEntryAt(1, "vuequests_detail_options",
                Component.translatable("fancymenu.vuequests.detail_part.options"), options);
        ContextMenu position = new ContextMenu();
        position.addClickableEntry("pinned", Component.translatable("fancymenu.vuequests.detail_part.pinned"),
                (menu, entry) -> selectFollow(menu, null));
        for (LinearQuestDetailPart part : LinearQuestDetailPart.values()) {
            if (part != element.part()) {
                position.addClickableEntry(part.id, QuestDetailElementBuilder.name(part), (menu, entry) -> selectFollow(menu, part));
            }
        }
        options.addSubMenuEntry("vuequests_follow", Component.empty(), position)
                .setLabelSupplier((menu, entry) -> element.followPart() == null
                        ? Component.translatable("fancymenu.vuequests.detail_part.position_pinned")
                        : Component.translatable("fancymenu.vuequests.detail_part.position_follow",
                        QuestDetailElementBuilder.name(element.followPart())));
        addOption(options, element.gap);
        if (element.hasAlignment()) {
            ContextMenu alignments = new ContextMenu();
            for (LinearQuestTextAlignment alignment : LinearQuestTextAlignment.values()) {
                alignments.addClickableEntry(alignment.name(), alignmentName(alignment), (menu, entry) -> {
                    saveSnapshot();
                    element.setTextAlignment(alignment);
                    menu.closeMenuChain();
                });
            }
            options.addSubMenuEntry("vuequests_text_alignment", Component.empty(), alignments)
                    .setLabelSupplier((menu, entry) -> Component.translatable("fancymenu.vuequests.detail_part.current_alignment",
                            alignmentName(element.textAlignment())));
        }
        addOption(options, element.titleScale);
        addOption(options, element.spacing);
        addToggle(options, "show_header", element.showHeader);
        addOption(options, element.headerLabel);
        addToggle(options, "show_decoration", element.showDecoration);
        addOption(options, element.decoration);
    }

    private void selectFollow(ContextMenu menu, LinearQuestDetailPart part) {
        saveSnapshot();
        element.setFollowPart(part);
        menu.closeMenuChain();
    }

    private Component alignmentName(LinearQuestTextAlignment alignment) {
        return Component.translatable("fancymenu.vuequests.detail_part.alignment_" + alignment.name().toLowerCase(Locale.ROOT));
    }

    private void addOption(ContextMenu menu, Property<?> property) {
        if (property != null) {
            property.buildContextMenuEntryAndAddTo(menu, this);
        }
    }

    private void addToggle(ContextMenu options, String id, BooleanProperty property) {
        if (property == null) {
            return;
        }
        Component label = Component.translatable("fancymenu.vuequests.detail_part." + id);
        options.addClickableEntry(id, Component.empty(), (menu, entry) -> {
            saveSnapshot();
            property.set(!property.getBoolean());
            menu.closeMenuChain();
        }).setLabelSupplier((menu, entry) -> Component.translatable("fancymenu.vuequests.detail_part.toggle", label,
                Component.translatable(property.getBoolean() ? "options.on" : "options.off")));
    }

    @Override
    protected void tick() {
        super.tick();
        QuestPanelElement parent = element.editorParent.get();
        if (parent == null || (!isDragged() && !isGettingResized())) {
            return;
        }
        if (isGettingResized() && element.advancedWidth.isDefault()) {
            int available = parent.getAbsoluteWidth() - Math.max(0, element.posOffsetX);
            element.baseWidth = Mth.clamp(element.baseWidth, 1, Math.max(1, available));
        }
        if (isGettingResized() && element.advancedHeight.isDefault()) {
            int available = parent.getAbsoluteHeight() - Math.max(0, element.posOffsetY);
            element.baseHeight = Mth.clamp(element.baseHeight, 1, Math.max(1, available));
        }
        if (element.advancedX.isDefault()) {
            element.posOffsetX = Mth.clamp(element.posOffsetX, 0, parent.getAbsoluteWidth() - getWidth());
        }
        if (element.advancedY.isDefault()) {
            element.posOffsetY = Mth.clamp(element.posOffsetY, 0, parent.getAbsoluteHeight() - getHeight());
        }
    }
}
