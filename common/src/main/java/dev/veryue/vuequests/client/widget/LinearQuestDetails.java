package dev.veryue.vuequests.client.widget;

import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.util.ClientRenderUtil;
import dev.veryue.vuequests.client.util.RenderAnimation;
import dev.veryue.vuequests.models.objective.ObjectiveAcknowledge;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LinearQuestDetails extends AbstractScrollWidget {

    public static final float TITLE_SCALE = 1.5F;
    private static final int GAP = 12;
    private static final int ICON = 16;
    private static final int REWARD_STEP = 26;
    private static final float OBJECTIVE_INSET = 6;
    private static final long INSET_DURATION_MS = 160;
    private static final float REWARD_HOVER_SCALE = 1.25F;
    private static final long REWARD_SCALE_DURATION_MS = 180;
    private static final int REWARD_PADDING = Mth.ceil(ICON * (REWARD_HOVER_SCALE - 1) / 2);
    private static final Component OBJECTIVES = Component.translatable("screen.vuequests.objectives");
    private static final Component REWARDS = Component.translatable("screen.vuequests.rewards");
    private static final Component NONE = Component.translatable("screen.vuequests.none");
    private final Font font;
    private final Predicate<Style> onTextClick;
    private final Consumer<RewardType<?>> onRewardClick;
    private final Consumer<QuestObjective<?, ?>> onObjectiveClick;
    private Quest quest;
    private List<FormattedCharSequence> title = List.of();
    private List<FormattedCharSequence> description = List.of();
    private List<List<FormattedCharSequence>> actions = List.of();
    private List<ItemStack> objectiveIcons = List.of();
    private List<ItemStack> rewardIcons = List.of();
    private int[] objectiveOffsets = new int[0];
    private int progressWidth;
    private boolean progressBelow;
    private int descriptionTop;
    private int objectivesHeading;
    private int objectivesTop;
    private int rewardsHeading;
    private int rewardsTop;
    private int rewardColumns = 1;
    private int contentHeight;
    private int keyboardAction = -1;
    private final Map<UUID, RenderAnimation> objectiveInsets = new HashMap<>();
    private final Map<UUID, RenderAnimation> rewardScales = new HashMap<>();

    public LinearQuestDetails(Font font, Predicate<Style> onTextClick, Consumer<RewardType<?>> onRewardClick,
                              Consumer<QuestObjective<?, ?>> onObjectiveClick) {
        super(0, 0, 1, 1, Component.empty());
        this.font = font;
        this.onTextClick = onTextClick;
        this.onRewardClick = onRewardClick;
        this.onObjectiveClick = onObjectiveClick;
    }

    public void setQuest(Quest quest) {
        boolean changed = this.quest == null || quest == null || !this.quest.questId().equals(quest.questId());
        this.quest = quest;
        if (changed) {
            setScrollAmount(0);
            keyboardAction = -1;
            objectiveInsets.clear();
            rewardScales.clear();
        } else if (quest != null) {
            objectiveInsets.keySet().retainAll(quest.objectives().stream().map(QuestObjective::getObjectiveId).collect(Collectors.toSet()));
            rewardScales.keySet().retainAll(quest.rewards().stream().map(RewardType::getRewardId).collect(Collectors.toSet()));
        }
        setMessage(quest == null ? Component.translatable("screen.vuequests.no_available_quests") : quest.display().title());
        objectiveIcons = quest == null ? List.of() : quest.objectives().stream().map(QuestObjective::getIconStack).toList();
        rewardIcons = quest == null ? List.of() : quest.rewards().stream().map(RewardType::getIconStack).toList();
        fit(getWidth(), getHeight());
    }

    public Quest getQuest() {
        return quest;
    }

    public void fit(int width, int height) {
        setSize(Math.max(1, width), Math.max(1, height));
        if (quest == null) {
            contentHeight = 0;
            return;
        }
        if (keyboardAction >= quest.objectives().size() + quest.rewards().size()) keyboardAction = -1;
        int textWidth = Math.max(1, width - totalInnerPadding());
        title = font.split(quest.display().title(), Math.max(1, (int) (textWidth / TITLE_SCALE)));
        description = quest.display().description().getString().isBlank() ? List.of()
                : font.split(quest.display().description(), textWidth);
        descriptionTop = Mth.ceil(title.size() * lineHeight() * TITLE_SCALE) + GAP;
        objectivesHeading = descriptionTop + description.size() * lineHeight() + GAP;
        objectivesTop = objectivesHeading + font.lineHeight + GAP;
        progressWidth = quest.objectives().stream().mapToInt(objective -> font.width(objective.getProgressText(ClientQuestData.getUser())))
                .max().orElse(0);
        progressBelow = textWidth - ICON - 8 - OBJECTIVE_INSET - progressWidth - 8 < 80;
        int actionWidth = Math.max(1, textWidth - ICON - 8 - (int) OBJECTIVE_INSET - (progressBelow ? 0 : progressWidth + 8));
        actions = quest.objectives().stream().map(objective -> font.split(objective.getActionText(), actionWidth)).toList();
        objectiveOffsets = new int[actions.size() + 1];
        for (int i = 0; i < actions.size(); i++) {
            objectiveOffsets[i + 1] = objectiveOffsets[i] + Math.max(ICON, actions.get(i).size() * lineHeight() + 3)
                    + (progressBelow ? lineHeight() + 2 : 0) + 7;
        }
        rewardsHeading = objectivesTop + (actions.isEmpty() ? lineHeight() : objectiveOffsets[actions.size()]) + GAP;
        rewardsTop = rewardsHeading + font.lineHeight + GAP;
        rewardColumns = Math.max(1, (textWidth + REWARD_STEP - ICON) / REWARD_STEP);
        contentHeight = rewardsTop + (rewardIcons.isEmpty() ? lineHeight()
                : (Mth.positiveCeilDiv(rewardIcons.size(), rewardColumns) - 1) * REWARD_STEP + ICON + REWARD_PADDING);
        setScrollAmount(scrollAmount());
    }

    @Override
    protected void renderBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (quest == null) {
            int y = top();
            for (FormattedCharSequence line : font.split(getMessage(), Math.max(1, getWidth() - totalInnerPadding()))) {
                graphics.drawString(font, line, textX(), y, 0xFFBBBBBB);
                y += lineHeight();
            }
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + getWidth() / 2F, top(), 0);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE, 1);
        for (int i = 0; i < title.size(); i++) {
            graphics.drawString(font, title.get(i), -font.width(title.get(i)) / 2, i * lineHeight(), 0xFFFFFFFF);
        }
        graphics.pose().popPose();
        drawLines(graphics, description, textX(), top() + descriptionTop, 0xFFCCCCCC);
        LinearQuestSprites.heading(graphics, font, OBJECTIVES, textX(), top() + objectivesHeading, textWidth());
        if (actions.isEmpty()) graphics.drawCenteredString(font, NONE, getX() + getWidth() / 2, top() + objectivesTop, 0xFFAAAAAA);
        int hoveredObjective = inside(mouseX, mouseY) ? objectiveAt(mouseY) : -1;
        for (int i = 0; i < actions.size(); i++) {
            int y = top() + objectivesTop + objectiveOffsets[i];
            QuestObjective<?, ?> objective = quest.objectives().get(i);
            RenderAnimation animation = objectiveInsets.computeIfAbsent(objective.getObjectiveId(), id -> new RenderAnimation(0));
            ItemStack icon = objectiveIcons.get(i);
            List<FormattedCharSequence> lines = actions.get(i);
            boolean inset = hoveredObjective == i || (isFocused() && keyboardAction == i);
            ClientRenderUtil.renderInset(graphics.pose(), animation, inset, OBJECTIVE_INSET, INSET_DURATION_MS, amount -> {
                if (objective instanceof ObjectiveAcknowledge) {
                    QuestWidgetSprites.blitAcknowledgement(graphics, textX(), y,
                            ClientQuestData.getUser() != null && ClientQuestData.getUser().hasCompletedObjective(objective));
                } else graphics.renderItem(icon, textX(), y);
                drawLines(graphics, lines, textX() + ICON + 8, y + 3, 0xFFDDDDDD);
            });
            Component progress = objective.getProgressText(ClientQuestData.getUser());
            int progressY = progressBelow ? y + Math.max(ICON, actions.get(i).size() * lineHeight() + 3) + 2 : y + 3;
            graphics.drawString(font, progress, textX() + textWidth() - font.width(progress), progressY, 0xFFCCCCCC);
        }
        LinearQuestSprites.heading(graphics, font, REWARDS, textX(), top() + rewardsHeading, textWidth());
        if (rewardIcons.isEmpty()) graphics.drawCenteredString(font, NONE, getX() + getWidth() / 2, top() + rewardsTop, 0xFFAAAAAA);
        int hoveredReward = inside(mouseX, mouseY) ? rewardAt(mouseX, mouseY) : -1;
        for (int i = 0; i < rewardIcons.size(); i++) {
            int x = rewardX(i);
            int y = top() + rewardsTop + i / rewardColumns * REWARD_STEP;
            RewardType<?> reward = quest.rewards().get(i);
            ItemStack icon = rewardIcons.get(i);
            RenderAnimation animation = rewardScales.computeIfAbsent(reward.getRewardId(), id -> new RenderAnimation(1));
            ClientRenderUtil.renderScaled(graphics.pose(), animation, hoveredReward == i, REWARD_HOVER_SCALE, REWARD_SCALE_DURATION_MS,
                    x + ICON / 2F, y + ICON / 2F, () -> {
                        graphics.renderItem(icon, x, y);
                        if (ClientQuestData.isRewardClaimed(reward) || ClientQuestData.canClaimRewards(quest)) {
                            QuestWidgetSprites.blitClaimBadge(graphics, x, y, ClientQuestData.isRewardClaimed(reward));
                        }
                    });
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (quest == null || !inside(mouseX, mouseY)) return;
        int reward = rewardAt(mouseX, mouseY);
        if (reward >= 0) {
            RewardType<?> value = quest.rewards().get(reward);
            if (value instanceof RewardItem item) {
                ItemStack stack = item.getStack();
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
                lines.add(Component.translatable("screen.vuequests.reward.quantity", stack.getCount()).withStyle(ChatFormatting.GRAY));
                graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
            } else graphics.renderTooltip(font, value.getDisplayName(), mouseX, mouseY);
        } else {
            Style style = styleAt(mouseX, mouseY);
            if (style != null) graphics.renderComponentHoverEffect(font, style, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (quest == null || button != 0 || !inside(mouseX, mouseY)) return false;
        int objective = objectiveAt(mouseY);
        if (objective >= 0) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            keyboardAction = objective;
            interact(keyboardAction);
            return true;
        }
        int reward = rewardAt(mouseX, mouseY);
        if (reward >= 0) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            keyboardAction = actions.size() + reward;
            interact(keyboardAction);
            return true;
        }
        Style style = styleAt(mouseX, mouseY);
        if (style != null && onTextClick.test(style)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void interact(int index) {
        if (index < 0 || !ClientQuestData.prerequisitesMet(quest)) return;
        if (index < actions.size()) {
            QuestObjective<?, ?> objective = quest.objectives().get(index);
            if (objective.requiresInteraction() && ClientQuestData.getUser() != null
                    && !ClientQuestData.getUser().hasCompletedObjective(objective)) onObjectiveClick.accept(objective);
        } else {
            RewardType<?> reward = quest.rewards().get(index - actions.size());
            if (ClientQuestData.canClaimRewards(quest) && !ClientQuestData.isRewardClaimed(reward)) onRewardClick.accept(reward);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (quest == null) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> setScrollAmount(scrollAmount() - 22);
            case GLFW.GLFW_KEY_DOWN -> setScrollAmount(scrollAmount() + 22);
            case GLFW.GLFW_KEY_PAGE_UP -> setScrollAmount(scrollAmount() - getHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> setScrollAmount(scrollAmount() + getHeight());
            case GLFW.GLFW_KEY_HOME -> setScrollAmount(0);
            case GLFW.GLFW_KEY_END -> setScrollAmount(getMaxScrollAmount());
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                int count = actions.size() + rewardIcons.size();
                if (count == 0) return false;
                keyboardAction = Math.floorMod(keyboardAction + (keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1), count);
                int y = keyboardAction < actions.size() ? objectivesTop + objectiveOffsets[keyboardAction]
                        : rewardsTop + (keyboardAction - actions.size()) / rewardColumns * REWARD_STEP;
                if (y < scrollAmount()) setScrollAmount(y);
                if (y + ICON > scrollAmount() + height - totalInnerPadding()) setScrollAmount(y + ICON - height + totalInnerPadding());
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> { interact(keyboardAction); }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    private int objectiveAt(double mouseY) {
        double y = mouseY + scrollAmount() - top() - objectivesTop;
        for (int i = 0; i < actions.size(); i++) {
            if (y >= objectiveOffsets[i] && y < objectiveOffsets[i + 1] - 7) return i;
        }
        return -1;
    }

    private int rewardAt(double mouseX, double mouseY) {
        double y = mouseY + scrollAmount() - top() - rewardsTop;
        if (y < -REWARD_PADDING) return -1;
        int row = (int) ((y + REWARD_PADDING) / REWARD_STEP);
        if (y - row * REWARD_STEP >= ICON + REWARD_PADDING) return -1;
        int first = row * rewardColumns;
        for (int i = first; i < Math.min(first + rewardColumns, rewardIcons.size()); i++) {
            if (mouseX >= rewardX(i) - REWARD_PADDING && mouseX < rewardX(i) + ICON + REWARD_PADDING) return i;
        }
        return -1;
    }

    private int rewardX(int index) {
        int first = index / rewardColumns * rewardColumns;
        int count = Math.min(rewardColumns, rewardIcons.size() - first);
        return getX() + (getWidth() - ((count - 1) * REWARD_STEP + ICON)) / 2 + (index - first) * REWARD_STEP;
    }

    private Style styleAt(double mouseX, double mouseY) {
        double y = mouseY + scrollAmount() - top() - descriptionTop;
        int index = (int) (y / lineHeight());
        if (y < 0 || index >= description.size() || y % lineHeight() >= font.lineHeight) return null;
        double x = mouseX - textX();
        return x < 0 || x >= font.width(description.get(index)) ? null
                : font.getSplitter().componentStyleAtWidth(description.get(index), (int) x);
    }

    private boolean inside(double x, double y) {
        return active && visible && x >= getX() + 1 && x < getRight() - 1 && y >= getY() + 1 && y < getBottom() - 1;
    }

    private void drawLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int y, int color) {
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color);
            y += lineHeight();
        }
    }

    private int lineHeight() { return font.lineHeight + 2; }
    private int textX() { return getX() + innerPadding(); }
    private int textWidth() { return Math.max(1, getWidth() - totalInnerPadding()); }
    private int top() { return getY() + innerPadding(); }
    @Override protected int getInnerHeight() { return contentHeight; }
    @Override protected boolean scrollbarVisible() { return false; }
    @Override protected double scrollRate() { return 22; }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (quest == null) return;
        List<Component> contents = new ArrayList<>();
        contents.add(quest.display().description());
        contents.add(OBJECTIVES);
        quest.objectives().forEach(objective -> contents.add(Component.empty().append(objective.getActionText())
                .append(" ").append(objective.getProgressText(ClientQuestData.getUser()))));
        contents.add(REWARDS);
        quest.rewards().forEach(reward -> contents.add(reward.getDisplayName()));
        output.add(NarratedElementType.HINT, contents.toArray(Component[]::new));
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.linear_details_usage"));
    }
}
