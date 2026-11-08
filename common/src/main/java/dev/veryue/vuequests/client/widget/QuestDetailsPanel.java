package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.reward.RewardExperience;
import dev.veryue.vuequests.models.reward.RewardItem;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class QuestDetailsPanel extends AbstractScrollWidget {

    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int HEADING_COLOR = 0xFFFFFFFF;
    private static final int SECTION_GAP = 8;
    private static final int ICON_SIZE = 16;
    private static final int ICON_STEP = 22;
    private static final int MAX_ICON_COLUMNS = 8;
    private static final Component OBJECTIVES = heading("objectives");
    private static final Component DESCRIPTION = heading("description");
    private static final Component REWARDS = heading("rewards");
    private static final Component NONE = Component.translatable("screen.vuequests.none");

    private final Font font;
    private final Quest quest;
    private final Predicate<Style> onTextClick;
    private final List<ItemStack> objectiveIcons;
    private final List<ItemStack> rewardIcons;
    private List<FormattedCharSequence> titleLines = List.of();
    private List<FormattedCharSequence> descriptionLines = List.of();
    private int iconColumns;
    private int contentHeight;
    private int headingCenterOffset;

    public QuestDetailsPanel(Font font, Quest quest, Predicate<Style> onTextClick) {
        super(0, 0, 0, 0, quest.display().title());
        this.font = font;
        this.quest = quest;
        this.onTextClick = onTextClick;
        objectiveIcons = quest.objectives().stream().map(QuestObjective::getIconStack).toList();
        rewardIcons = quest.rewards().stream().map(RewardType::getIconStack).toList();
    }

    public void fit(int width, int maxHeight, int headingCenterOffset) {
        this.headingCenterOffset = headingCenterOffset;
        int textWidth = Math.max(1, width - totalInnerPadding());
        int centeredWidth = Math.max(1, textWidth - 2 * headingCenterOffset);
        titleLines = font.split(Component.empty().withStyle(ChatFormatting.BOLD).append(getMessage()), centeredWidth);
        Component description = quest.display().description();
        descriptionLines = description.getString().isBlank() ? List.of() : font.split(description, textWidth);
        iconColumns = Mth.clamp((centeredWidth + ICON_STEP - ICON_SIZE) / ICON_STEP, 1, MAX_ICON_COLUMNS);
        contentHeight = rewardsTop() - contentTop() + iconGridHeight(rewardIcons.size());
        setSize(width, Math.min(maxHeight, contentHeight + totalInnerPadding()));
        setScrollAmount(scrollAmount());
    }

    private static Component heading(String key) {
        return Component.translatable("screen.vuequests." + key).withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected void renderBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawLines(graphics, titleLines, headingCenterX(), contentTop(), true);
        drawHeading(graphics, OBJECTIVES, objectivesTop());
        drawIconGrid(graphics, objectiveIcons, objectivesTop(),
                index -> quest.objectives().get(index).getProgressText(ClientQuestData.getUser()), false);
        drawHeading(graphics, DESCRIPTION, descriptionTop());
        drawLines(graphics, descriptionLines, textX(), descriptionTop(), false);
        drawHeading(graphics, REWARDS, rewardsTop());
        drawIconGrid(graphics, rewardIcons, rewardsTop(), index -> rewardAmount(quest.rewards().get(index)), true);
    }

    private void drawIconGrid(GuiGraphics graphics, List<ItemStack> icons, int top, IntFunction<Component> labelAt,
                              boolean showClaimBadges) {
        if (icons.isEmpty()) {
            graphics.drawString(font, NONE, headingCenterX() - font.width(NONE) / 2, top, TEXT_COLOR, false);
            return;
        }
        boolean canClaimRewards = showClaimBadges && ClientQuestData.isQuestComplete(quest) && ClientQuestData.prerequisitesMet(quest);
        for (int i = 0; i < icons.size(); i++) {
            int y = top + i / iconColumns * ICON_STEP;
            if (withinContentAreaTopBottom(y, y + ICON_SIZE)) {
                int column = i % iconColumns;
                int x = iconRowLeft(i - column, icons.size()) + column * ICON_STEP;
                graphics.renderItem(icons.get(i), x, y);
                if (showClaimBadges) {
                    boolean claimed = ClientQuestData.isRewardClaimed(quest.rewards().get(i));
                    if (claimed || canClaimRewards) {
                        QuestWidgetSprites.blitClaimBadge(graphics, x, y, claimed);
                    }
                }
                Component label = labelAt.apply(i);
                if (label != null) {
                    drawIconLabel(graphics, label, x, y);
                }
            }
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (!insideClip(mouseX, mouseY)) {
            return;
        }
        double contentY = mouseY + scrollAmount();
        int objectiveIndex = iconAt(mouseX, contentY - objectivesTop(), objectiveIcons.size());
        if (objectiveIndex >= 0) {
            Component tooltip = objectiveTooltip(quest.objectives().get(objectiveIndex));
            graphics.renderTooltip(font, font.split(tooltip, Math.min(170, getWidth())), mouseX, mouseY);
            return;
        }
        int rewardIndex = iconAt(mouseX, contentY - rewardsTop(), rewardIcons.size());
        if (rewardIndex >= 0) {
            renderRewardTooltip(graphics, rewardIndex, mouseX, mouseY);
            return;
        }
        Style style = styleAt(mouseX, mouseY);
        if (style != null) {
            graphics.renderComponentHoverEffect(font, style, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideClip(mouseX, mouseY)) {
            Style style = styleAt(mouseX, mouseY);
            if (style != null && onTextClick.test(style)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> setScrollAmount(scrollAmount() - getHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> setScrollAmount(scrollAmount() + getHeight());
            case GLFW.GLFW_KEY_HOME -> setScrollAmount(0);
            case GLFW.GLFW_KEY_END -> setScrollAmount(getMaxScrollAmount());
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    private Style styleAt(double mouseX, double mouseY) {
        double contentY = mouseY + scrollAmount();
        if (contentY < objectivesTop() - lineHeight() - SECTION_GAP) {
            return lineStyleAt(titleLines, mouseX, contentY - contentTop(), true);
        }
        return lineStyleAt(descriptionLines, mouseX, contentY - descriptionTop(), false);
    }

    private Style lineStyleAt(List<FormattedCharSequence> lines, double mouseX, double offsetY, boolean centered) {
        int line = (int) (offsetY / lineHeight());
        if (offsetY < 0 || line >= lines.size() || offsetY % lineHeight() >= font.lineHeight) {
            return null;
        }
        FormattedCharSequence text = lines.get(line);
        int width = font.width(text);
        int left = centered ? headingCenterX() - width / 2 : textX();
        double x = mouseX - left;
        return x >= 0 && x < width ? font.getSplitter().componentStyleAtWidth(text, (int) x) : null;
    }

    private boolean insideClip(double mouseX, double mouseY) {
        return mouseX >= getX() + 1 && mouseX < getRight() - 1 && mouseY >= getY() + 1 && mouseY < getBottom() - 1;
    }

    private void drawHeading(GuiGraphics graphics, Component heading, int sectionTop) {
        int left = headingCenterX() - font.width(heading) / 2;
        graphics.drawString(font, heading, left, sectionTop - lineHeight(), HEADING_COLOR, false);
    }

    private void drawIconLabel(GuiGraphics graphics, Component label, int x, int y) {
        int textWidth = font.width(label);
        float scale = Math.min(0.75F, (ICON_SIZE - 1F) / Math.max(1, textWidth));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + ICON_SIZE - 1, y + ICON_SIZE - 1, 200);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, label, -textWidth, -font.lineHeight, 0xFFFFFFFF, true);
        pose.popPose();
    }

    private Component objectiveTooltip(QuestObjective<?, ?> objective) {
        return Component.empty().append(objective.getActionText()).append("\n")
                .append(objective.getProgressText(ClientQuestData.getUser()));
    }

    private Component rewardAmount(RewardType<?> reward) {
        return switch (reward) {
            case RewardItem item -> Component.literal(Integer.toString(item.getStack().getCount()));
            case RewardExperience experience -> Component.literal(Integer.toString(experience.getValue()));
            default -> null;
        };
    }

    private void renderRewardTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        RewardType<?> reward = quest.rewards().get(index);
        if (reward instanceof RewardItem item) {
            ItemStack stack = item.getStack();
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            lines.add(Component.translatable("screen.vuequests.reward.quantity", stack.getCount()).withStyle(ChatFormatting.GRAY));
            graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
        } else {
            graphics.renderTooltip(font, font.split(reward.getDisplayName(), Math.min(170, getWidth())), mouseX, mouseY);
        }
    }

    private int iconAt(int mouseX, double y, int iconCount) {
        if (y < 0 || y % ICON_STEP >= ICON_SIZE) {
            return -1;
        }
        int rowStart = (int) (y / ICON_STEP) * iconColumns;
        if (rowStart >= iconCount) {
            return -1;
        }
        int x = mouseX - iconRowLeft(rowStart, iconCount);
        if (x < 0 || x % ICON_STEP >= ICON_SIZE) {
            return -1;
        }
        int index = rowStart + x / ICON_STEP;
        return index < Math.min(rowStart + iconColumns, iconCount) ? index : -1;
    }

    private int iconRowLeft(int rowStart, int iconCount) {
        int iconsInRow = Math.min(iconColumns, iconCount - rowStart);
        return headingCenterX() - ((iconsInRow - 1) * ICON_STEP + ICON_SIZE) / 2;
    }

    private void drawLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int anchorX, int y, boolean centered) {
        for (FormattedCharSequence line : lines) {
            if (withinContentAreaTopBottom(y, y + font.lineHeight)) {
                int left = centered ? anchorX - font.width(line) / 2 : anchorX;
                graphics.drawString(font, line, left, y, TEXT_COLOR, false);
            }
            y += lineHeight();
        }
    }

    private int textX() {
        return getX() + innerPadding();
    }

    private int headingCenterX() {
        return getX() + getWidth() / 2 + headingCenterOffset;
    }

    private int contentTop() {
        return getY() + innerPadding();
    }

    private int lineHeight() {
        return font.lineHeight + 2;
    }

    private int objectivesTop() {
        return contentTop() + titleLines.size() * lineHeight() + SECTION_GAP + lineHeight();
    }

    private int descriptionTop() {
        return objectivesTop() + iconGridHeight(objectiveIcons.size()) + SECTION_GAP + lineHeight();
    }

    private int rewardsTop() {
        return descriptionTop() + descriptionLines.size() * lineHeight() + SECTION_GAP + lineHeight();
    }

    private int iconGridHeight(int iconCount) {
        int rows = Mth.positiveCeilDiv(iconCount, iconColumns);
        return rows == 0 ? lineHeight() : (rows - 1) * ICON_STEP + ICON_SIZE;
    }

    @Override
    protected int getInnerHeight() {
        return contentHeight;
    }

    @Override
    protected boolean scrollbarVisible() {
        return getMaxScrollAmount() > 0;
    }

    @Override
    protected double scrollRate() {
        return 18;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        List<Component> contents = new ArrayList<>();
        contents.add(OBJECTIVES);
        quest.objectives().forEach(objective -> contents.add(objectiveTooltip(objective)));
        contents.add(DESCRIPTION);
        contents.add(quest.display().description());
        contents.add(REWARDS);
        quest.rewards().forEach(reward -> contents.add(reward.getDisplayName()));
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.HINT, contents.toArray(Component[]::new));
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.details_usage"));
    }
}
