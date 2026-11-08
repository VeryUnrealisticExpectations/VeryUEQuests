package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestNodeWidget extends Button {

    public static final int SIZE = 24;
    private static final float LOCKED_BRIGHTNESS = 0.35F;
    private static final int SUMMARY_WORDS = 10;
    private static final Pattern WORD = Pattern.compile("\\S+", Pattern.UNICODE_CHARACTER_CLASS);

    private final Quest quest;
    private final ItemStack icon;
    private final Tooltip questTooltip;
    private boolean locked;

    public QuestNodeWidget(Quest quest, Consumer<Quest> onSelect) {
        super(0, 0, SIZE, SIZE, quest.display().title(), button -> onSelect.accept(quest), DEFAULT_NARRATION);
        this.quest = quest;
        icon = new ItemStack(quest.display().icon());
        questTooltip = Tooltip.create(tooltipText(quest));
    }

    private static Component tooltipText(Quest quest) {
        MutableComponent tooltip = Component.empty().append(quest.display().title());
        Matcher words = WORD.matcher(quest.display().description().getString());
        StringBuilder summary = new StringBuilder();
        for (int count = 0; count < SUMMARY_WORDS && words.find(); count++) {
            if (count > 0) {
                summary.append(' ');
            }
            summary.append(words.group());
        }
        if (!summary.isEmpty()) {
            tooltip.append("\n").append(summary.toString());
        }
        if (words.find()) {
            tooltip.append("\n").append(Component.translatable("screen.vuequests.read_more"));
        }
        return tooltip;
    }

    public void renderQuestTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.renderTooltip(font, questTooltip.toCharSequence(Minecraft.getInstance()), mouseX, mouseY);
    }

    public Quest getQuest() {
        return quest;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(getX(), getY(), 0);
            pose.scale(getWidth() / (float) SIZE, getHeight() / (float) SIZE, 1);
            float brightness = locked ? LOCKED_BRIGHTNESS : 1;
            QuestWidgetSprites.blit(graphics, quest.display().backgroundSprite(), 0, 0, SIZE, SIZE, brightness);
            graphics.flush();
            graphics.setColor(brightness, brightness, brightness, 1);
            graphics.renderItem(icon, 4, 4);
        } finally {
            graphics.flush();
            graphics.setColor(1, 1, 1, 1);
            pose.popPose();
        }
    }
}
