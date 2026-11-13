package dev.veryue.vuequests.client.widget;

import dev.veryue.vuequests.client.util.ClientTextUtil;
import dev.veryue.vuequests.client.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import org.joml.Matrix3x2fStack;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.ObjectiveAcknowledge;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.reward.RewardItem;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class QuestDetailsPanel extends AbstractTextAreaWidget {

    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int HEADING_COLOR = 0xFFFFFFFF;
    private static final int SECTION_GAP = 8;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 6;
    private static final int OBJECTIVE_LABEL_WIDTH = 28;
    private static final int OBJECTIVE_HEIGHT = ICON_SIZE + 2 + QuestWidgetSprites.OBJECTIVE_PROGRESS_HEIGHT;
    private static final int MAX_ICON_COLUMNS = 8;
    private static final Component OBJECTIVES = heading("objectives");
    private static final Component DESCRIPTION = heading("description");
    private static final Component REWARDS = heading("rewards");
    private static final Component NONE = Component.translatable("screen.vuequests.none");

    private final Font font;
    private final Quest quest;
    private final Predicate<Style> onTextClick;
    private final Consumer<RewardType<?>> onRewardClick;
    private final Consumer<QuestObjective<?, ?>> onObjectiveClick;
    private final List<ItemStack> objectiveIcons;
    private final List<ItemStack> rewardIcons;
    private List<FormattedCharSequence> titleLines = List.of();
    private List<FormattedCharSequence> descriptionLines = List.of();
    private int iconColumns;
    private int objectiveColumns;
    private int contentHeight;
    private int headingCenterOffset;

    public QuestDetailsPanel(Font font, Quest quest, Predicate<Style> onTextClick, Consumer<RewardType<?>> onRewardClick,
                             Consumer<QuestObjective<?, ?>> onObjectiveClick) {
        super(0, 0, 0, 0, quest.display().title(), defaultSettings(18), false, false);
        this.font = font;
        this.quest = quest;
        this.onTextClick = onTextClick;
        this.onRewardClick = onRewardClick;
        this.onObjectiveClick = onObjectiveClick;
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
        iconColumns = Mth.clamp((centeredWidth + ICON_GAP) / (ICON_SIZE + ICON_GAP), 1, MAX_ICON_COLUMNS);
        objectiveColumns = Mth.clamp((centeredWidth + ICON_GAP) / (OBJECTIVE_LABEL_WIDTH + ICON_GAP), 1, MAX_ICON_COLUMNS);
        contentHeight = rewardsTop() - contentTop() + iconGridHeight(rewardIcons.size(), iconColumns, ICON_SIZE);
        setSize(width, Math.min(maxHeight, contentHeight + totalInnerPadding()));
        setScrollAmount(scrollAmount());
    }

    private static Component heading(String key) {
        return Component.translatable("screen.vuequests." + key).withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawLines(graphics, titleLines, headingCenterX(), contentTop(), true);
        drawHeading(graphics, OBJECTIVES, objectivesTop());
        drawIconGrid(graphics, objectiveIcons, objectivesTop(),
                index -> quest.objectives().get(index).getIconLabel(ClientQuestData.getUser()), false);
        drawHeading(graphics, DESCRIPTION, descriptionTop());
        drawLines(graphics, descriptionLines, textX(), descriptionTop(), false);
        drawHeading(graphics, REWARDS, rewardsTop());
        drawIconGrid(graphics, rewardIcons, rewardsTop(), index -> quest.rewards().get(index).getIconLabel(), true);
    }

    private void drawIconGrid(GuiGraphicsExtractor graphics, List<ItemStack> icons, int top, IntFunction<Component> labelAt,
                              boolean showClaimBadges) {
        if (icons.isEmpty()) {
            graphics.text(font, NONE, headingCenterX() - font.width(NONE) / 2, top, TEXT_COLOR, false);
            return;
        }
        boolean canClaimRewards = showClaimBadges && ClientQuestData.canClaimRewards(quest);
        int columns = showClaimBadges ? iconColumns : objectiveColumns;
        int cellWidth = showClaimBadges ? ICON_SIZE : OBJECTIVE_LABEL_WIDTH;
        int cellHeight = showClaimBadges ? ICON_SIZE : OBJECTIVE_HEIGHT;
        for (int i = 0; i < icons.size(); i++) {
            int y = top + i / columns * (cellHeight + ICON_GAP);
            if (withinContentAreaTopBottom(y, y + cellHeight)) {
                int column = i % columns;
                int x = iconRowLeft(i - column, icons.size(), columns, cellWidth)
                        + column * (cellWidth + ICON_GAP) + (cellWidth - ICON_SIZE) / 2;
                boolean acknowledgement = !showClaimBadges && quest.objectives().get(i) instanceof ObjectiveAcknowledge;
                if (acknowledgement) {
                    QuestWidgetSprites.blitAcknowledgement(graphics, x, y,
                            ClientQuestData.getUser() != null && ClientQuestData.getUser().hasCompletedObjective(quest.objectives().get(i)));
                } else {
                    graphics.item(icons.get(i), x, y);
                }
                if (showClaimBadges) {
                    boolean claimed = ClientQuestData.isRewardClaimed(quest.rewards().get(i));
                    if (claimed || canClaimRewards) {
                        QuestWidgetSprites.blitClaimBadge(graphics, x, y, claimed);
                    }
                } else if (!acknowledgement) {
                    QuestWidgetSprites.blitObjectiveProgress(graphics, x, y + ICON_SIZE + 2,
                            quest.objectives().get(i).getProgressFraction(ClientQuestData.getUser()));
                }
                Component label = labelAt.apply(i);
                if (label != null && !acknowledgement) {
                    drawIconLabel(graphics, label, x, y, cellWidth);
                }
            }
        }
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        if (!insideClip(mouseX, mouseY)) {
            return;
        }
        double contentY = mouseY + scrollAmount();
        int objectiveIndex = iconAt(mouseX, contentY - objectivesTop(), objectiveIcons.size(), objectiveColumns,
                OBJECTIVE_LABEL_WIDTH, OBJECTIVE_HEIGHT);
        if (objectiveIndex >= 0) {
            Component tooltip = objectiveTooltip(quest.objectives().get(objectiveIndex));
            graphics.setTooltipForNextFrame(font, font.split(tooltip, Math.min(170, getWidth())), mouseX, mouseY);
            return;
        }
        int rewardIndex = iconAt(mouseX, contentY - rewardsTop(), rewardIcons.size(), iconColumns, ICON_SIZE, ICON_SIZE);
        if (rewardIndex >= 0) {
            renderRewardTooltip(graphics, rewardIndex, mouseX, mouseY);
            return;
        }
        Style style = styleAt(mouseX, mouseY);
        if (style != null) {
            ((GuiGraphicsExtractorAccessor) graphics).vuequests$componentHoverEffect(font, style, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (active && visible && button == InputConstants.MOUSE_BUTTON_LEFT && insideClip(mouseX, mouseY)) {
            int objectiveIndex = iconAt(mouseX, mouseY + scrollAmount() - objectivesTop(), objectiveIcons.size(), objectiveColumns,
                    OBJECTIVE_LABEL_WIDTH, OBJECTIVE_HEIGHT);
            if (objectiveIndex >= 0) {
                QuestObjective<?, ?> objective = quest.objectives().get(objectiveIndex);
                if (objective.requiresInteraction() && ClientQuestData.prerequisitesMet(quest) && ClientQuestData.getUser() != null
                        && !ClientQuestData.getUser().hasCompletedObjective(objective)) {
                    onObjectiveClick.accept(objective);
                    return true;
                }
            }
            int rewardIndex = iconAt(mouseX, mouseY + scrollAmount() - rewardsTop(), rewardIcons.size(), iconColumns, ICON_SIZE, ICON_SIZE);
            if (rewardIndex >= 0) {
                RewardType<?> reward = quest.rewards().get(rewardIndex);
                if (ClientQuestData.canClaimRewards(quest) && !ClientQuestData.isRewardClaimed(reward)) {
                    onRewardClick.accept(reward);
                    return true;
                }
            }
            Style style = styleAt(mouseX, mouseY);
            if (style != null && onTextClick.test(style)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        switch (keyCode) {
            case InputConstants.KEY_PAGEUP -> setScrollAmount(scrollAmount() - getHeight());
            case InputConstants.KEY_PAGEDOWN -> setScrollAmount(scrollAmount() + getHeight());
            case InputConstants.KEY_HOME -> setScrollAmount(0);
            case InputConstants.KEY_END -> setScrollAmount(maxScrollAmount());
            default -> { return super.keyPressed(event); }
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
        return x >= 0 && x < width ? ClientTextUtil.styleAtWidth(font, text, (int) x) : null;
    }

    private boolean insideClip(double mouseX, double mouseY) {
        return mouseX >= getX() + 1 && mouseX < getRight() - 1 && mouseY >= getY() + 1 && mouseY < getBottom() - 1;
    }

    private void drawHeading(GuiGraphicsExtractor graphics, Component heading, int sectionTop) {
        int left = headingCenterX() - font.width(heading) / 2;
        graphics.text(font, heading, left, sectionTop - lineHeight(), HEADING_COLOR, false);
    }

    private void drawIconLabel(GuiGraphicsExtractor graphics, Component label, int x, int y, int labelWidth) {
        int textWidth = font.width(label);
        float scale = Math.min(0.75F, (labelWidth - 1F) / Math.max(1, textWidth));
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + ICON_SIZE - 1, y + ICON_SIZE - 1);
        pose.scale(scale, scale);
        graphics.text(font, label, -textWidth, -font.lineHeight, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private Component objectiveTooltip(QuestObjective<?, ?> objective) {
        return Component.empty().append(objective.getActionText()).append("\n")
                .append(objective.getProgressText(ClientQuestData.getUser()));
    }

    private void renderRewardTooltip(GuiGraphicsExtractor graphics, int index, int mouseX, int mouseY) {
        RewardType<?> reward = quest.rewards().get(index);
        if (reward instanceof RewardItem item) {
            ItemStack stack = item.getStack();
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            lines.add(Component.translatable("screen.vuequests.reward.quantity", stack.getCount()).withStyle(ChatFormatting.GRAY));
            graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), mouseX, mouseY);
        } else {
            graphics.setTooltipForNextFrame(font, font.split(reward.getDisplayName(), Math.min(170, getWidth())), mouseX, mouseY);
        }
    }

    private int iconAt(double mouseX, double y, int iconCount, int columns, int cellWidth, int cellHeight) {
        int rowStep = cellHeight + ICON_GAP;
        int columnStep = cellWidth + ICON_GAP;
        if (y < 0 || y % rowStep >= cellHeight) {
            return -1;
        }
        int rowStart = (int) (y / rowStep) * columns;
        if (rowStart >= iconCount) {
            return -1;
        }
        double x = mouseX - iconRowLeft(rowStart, iconCount, columns, cellWidth);
        if (x < 0 || x % columnStep >= cellWidth) {
            return -1;
        }
        int index = rowStart + (int) (x / columnStep);
        return index < Math.min(rowStart + columns, iconCount) ? index : -1;
    }

    private int iconRowLeft(int rowStart, int iconCount, int columns, int cellWidth) {
        int iconsInRow = Math.min(columns, iconCount - rowStart);
        return headingCenterX() - ((iconsInRow - 1) * (cellWidth + ICON_GAP) + cellWidth) / 2;
    }

    private void drawLines(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines, int anchorX, int y, boolean centered) {
        for (FormattedCharSequence line : lines) {
            if (withinContentAreaTopBottom(y, y + font.lineHeight)) {
                int left = centered ? anchorX - font.width(line) / 2 : anchorX;
                graphics.text(font, line, left, y, TEXT_COLOR, false);
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
        return objectivesTop() + iconGridHeight(objectiveIcons.size(), objectiveColumns, OBJECTIVE_HEIGHT) + SECTION_GAP + lineHeight();
    }

    private int rewardsTop() {
        return descriptionTop() + descriptionLines.size() * lineHeight() + SECTION_GAP + lineHeight();
    }

    private int iconGridHeight(int iconCount, int columns, int cellHeight) {
        int rows = Mth.positiveCeilDiv(iconCount, columns);
        return rows == 0 ? lineHeight() : (rows - 1) * (cellHeight + ICON_GAP) + cellHeight;
    }

    @Override
    protected int getInnerHeight() {
        return contentHeight;
    }

    @Override
    protected boolean scrollable() {
        return maxScrollAmount() > 0;
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
