package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestNodeWidget extends Button {

    public static final int SIZE = 24;
    private static final float LOCKED_BRIGHTNESS = 0.35F;
    private static final int LOCKED_TINT = 0x595959;
    private static final int IN_PROGRESS_TINT = 0xFFF28B;
    private static final int UNCLAIMED_TINT = 0x9AFFAC;
    private static final int CLAIMED_TINT = 0x3B9B55;
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
        graphics.renderTooltip(font, tooltipLines(Minecraft.getInstance()), mouseX, mouseY);
    }

    private List<FormattedCharSequence> tooltipLines(Minecraft minecraft) {
        List<FormattedCharSequence> lines = questTooltip.toCharSequence(minecraft);
        if (!ClientQuestData.isDebugEnabled()) {
            return lines;
        }
        List<FormattedCharSequence> debugLines = new ArrayList<>(lines);
        debugLines.add(Component.translatable("screen.vuequests.debug.quest_id", quest.questId().toString())
                .withStyle(ChatFormatting.AQUA).getVisualOrderText());
        debugLines.add(Component.translatable("screen.vuequests.debug.copy_hint")
                .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        return debugLines;
    }

    public Quest getQuest() {
        return quest;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    private int backgroundTint(boolean complete, boolean allRewardsClaimed) {
        if (allRewardsClaimed && !quest.rewards().isEmpty()) {
            return CLAIMED_TINT;
        }
        if (complete) {
            return allRewardsClaimed ? CLAIMED_TINT : UNCLAIMED_TINT;
        }
        return locked ? LOCKED_TINT : IN_PROGRESS_TINT;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(getX(), getY(), 0);
            pose.scale(getWidth() / (float) SIZE, getHeight() / (float) SIZE, 1);
            float brightness = locked ? LOCKED_BRIGHTNESS : 1;
            boolean canClaimRewards = !quest.rewards().isEmpty() && ClientQuestData.canClaimRewards(quest);
            boolean complete = canClaimRewards || ClientQuestData.isQuestComplete(quest);
            boolean allRewardsClaimed = quest.rewards().stream().allMatch(ClientQuestData::isRewardClaimed);
            QuestWidgetSprites.blitTinted(graphics, quest.display().backgroundSprite(), 0, 0, SIZE, SIZE, backgroundTint(complete, allRewardsClaimed));
            graphics.flush();
            graphics.setColor(brightness, brightness, brightness, 1);
            graphics.renderItem(icon, 4, 4);
            boolean showBadge = quest.rewards().isEmpty() ? complete : allRewardsClaimed || canClaimRewards;
            if (showBadge) {
                graphics.flush();
                graphics.setColor(1, 1, 1, 1);
                QuestWidgetSprites.blitClaimBadge(graphics, 0, 0, allRewardsClaimed);
            }
        } finally {
            graphics.flush();
            graphics.setColor(1, 1, 1, 1);
            pose.popPose();
        }
    }
}
