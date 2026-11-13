package dev.veryue.vuequests.client.widget;

import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class QuestSectionButton extends Button {

    private final Font font;
    private final Section section;
    private final ItemStack icon;
    private boolean selected;

    public QuestSectionButton(Font font, Section section, Consumer<Section> onSelect) {
        super(0, 0, 0, 20, section.name(), button -> onSelect.accept(section), DEFAULT_NARRATION);
        this.font = font;
        this.section = section;
        icon = new ItemStack(section.icon());
    }

    public Section getSection() {
        return section;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        QuestWidgetSprites.blit(graphics, selected ? QuestWidgetSprites.SECTION_SELECTED : QuestWidgetSprites.SECTION, getX(), getY(), getWidth(), getHeight());
        if (getWidth() <= QuestSectionPanel.COLLAPSED_WIDTH) {
            graphics.item(icon, getX() + (getWidth() - 16) / 2, getY() + 2);
            return;
        }
        graphics.item(icon, getX() + 4, getY() + 2);
        graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(), Math.max(1, getWidth() - 28))),
                getX() + 24, getY() + 5, selected ? 0xFFFFFFFF : 0xFFD0D0D0);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        if (selected) {
            output.add(NarratedElementType.HINT, Component.translatable("narration.vuequests.selected_section"));
        }
    }
}
