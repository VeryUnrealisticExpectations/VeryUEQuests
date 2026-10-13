package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
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
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(getX(), getY(), 0);
            pose.scale(getWidth() / (float) SIZE, getHeight() / (float) SIZE, 1);
            QuestWidgetSprites.blit(graphics, QuestWidgetSprites.QUEST, 0, 0, SIZE, SIZE);
            graphics.renderItem(icon, 4, 4);
        } finally {
            pose.popPose();
        }
    }
}
