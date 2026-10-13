package dev.veryue.vuequests.client.widget;

import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class QuestNodeWidget extends Button {

    public static final int SIZE = 24;

    private final Quest quest;
    private final ItemStack icon;

    public QuestNodeWidget(Quest quest, Consumer<Quest> onSelect) {
        super(0, 0, SIZE, SIZE, Component.literal(quest.display().title()), button -> onSelect.accept(quest), DEFAULT_NARRATION);
        this.quest = quest;
        icon = new ItemStack(quest.display().icon());
        if (!quest.display().tooltip().isBlank()) {
            setTooltip(Tooltip.create(Component.literal(quest.display().tooltip())));
        }
    }

    public Quest getQuest() {
        return quest;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        QuestWidgetSprites.blit(graphics, QuestWidgetSprites.QUEST, getX(), getY(), getWidth(), getHeight());
        graphics.renderItem(icon, getX() + 4, getY() + 4);
    }
}
