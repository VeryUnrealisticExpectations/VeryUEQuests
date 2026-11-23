package dev.veryue.vuequests.client.widget.panels;

import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.util.ClientRenderUtil;
import dev.veryue.vuequests.client.util.RenderAnimation;
import dev.veryue.vuequests.client.widget.LinearQuestSprites;
import dev.veryue.vuequests.client.widget.QuestWidgetSprites;
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
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
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
    private static final LinearQuestDetailPart[] PARTS = LinearQuestDetailPart.values();
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
    private boolean progressBelow;
    private Map<LinearQuestDetailPart, LinearQuestDetailPlacement> placements = Map.of();
    private final Map<LinearQuestDetailPart, LinearQuestDetailBox> boxes = new EnumMap<>(LinearQuestDetailPart.class);
    private final Map<LinearQuestDetailPart, Integer> partScroll = new EnumMap<>(LinearQuestDetailPart.class);
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
            partScroll.clear();
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

    public void setLayout(int width, int height, Map<LinearQuestDetailPart, LinearQuestDetailPlacement> placements) {
        if (getWidth() != width || getHeight() != height || !this.placements.equals(placements)) {
            this.placements = placements;
            fit(width, height);
        }
    }

    private void fit(int width, int height) {
        setSize(Math.max(1, width), Math.max(1, height));
        boxes.clear();
        contentHeight = 0;
        if (quest == null) {
            return;
        }

        int titleWidth = partWidth(LinearQuestDetailPart.TITLE);
        title = font.split(quest.display().title(), Math.max(1, (int) (titleWidth / titleScale())));
        description = quest.display().description().getString().isBlank() ? List.of()
                : font.split(quest.display().description(), partWidth(LinearQuestDetailPart.DESCRIPTION));

        int objectiveWidth = partWidth(LinearQuestDetailPart.OBJECTIVES_LIST);
        int objectiveGap = objectiveGap();
        int progressWidth = quest.objectives().stream().mapToInt(objective -> font.width(objective.getProgressText(ClientQuestData.getUser())))
                .max().orElse(0);
        progressBelow = objectiveWidth - ICON - 8 - OBJECTIVE_INSET - progressWidth - 8 < 80;
        int actionWidth = Math.max(1, objectiveWidth - ICON - 8 - (int) OBJECTIVE_INSET - (progressBelow ? 0 : progressWidth + 8));
        actions = quest.objectives().stream().map(objective -> font.split(objective.getActionText(), actionWidth)).toList();
        objectiveOffsets = new int[actions.size() + 1];
        for (int i = 0; i < actions.size(); i++) {
            objectiveOffsets[i + 1] = objectiveOffsets[i] + Math.max(ICON, actions.get(i).size() * lineHeight() + 3)
                    + (progressBelow ? lineHeight() + 2 : 0) + objectiveGap;
        }

        int rewardStep = rewardStep();
        rewardColumns = Math.max(1, (partWidth(LinearQuestDetailPart.REWARDS_LIST) + rewardStep - ICON) / rewardStep);
        if (keyboardAction >= actions.size() + rewardIcons.size()) {
            keyboardAction = -1;
        }
        EnumSet<LinearQuestDetailPart> resolving = EnumSet.noneOf(LinearQuestDetailPart.class);
        for (LinearQuestDetailPart part : PARTS) {
            LinearQuestDetailBox box = resolveBox(part, resolving);
            if (!box.pinned()) {
                contentHeight = Math.max(contentHeight, box.bounds().bottom() - innerPadding());
            }
        }
        partScroll.replaceAll((part, scroll) -> Math.min(scroll, boxes.get(part).maxScroll()));
        setScrollAmount(scrollAmount());
    }

    private LinearQuestDetailBox resolveBox(LinearQuestDetailPart part, EnumSet<LinearQuestDetailPart> resolving) {
        LinearQuestDetailBox existing = boxes.get(part);
        if (existing != null) {
            return existing;
        }
        if (!resolving.add(part)) {
            return null;
        }
        LinearQuestDetailPlacement placement = placements.get(part);
        LinearQuestDetailPart follow = placement == null ? part.previous() : placement.follow();
        LinearQuestDetailBox previous = follow == null ? null : resolveBox(follow, resolving);
        boolean pinned = placement != null && previous == null;
        int left = placement == null ? innerPadding() : placement.bounds().left();
        int top = placement == null ? innerPadding() : placement.bounds().top();
        int flowTop = 0;
        if (previous != null) {
            int gap = placement == null ? GAP : placement.gap();
            top = previous.bounds().bottom() + (isHeaderVisible(follow) ? gap : 0);
            flowTop = previous.pinned() && previous.bounds().height() > 0 ? previous.bounds().bottom() : previous.flowTop();
        }
        int naturalHeight = naturalHeight(part);
        int height = pinned && isHeaderVisible(part) ? placement.bounds().height() : naturalHeight;
        LinearQuestDetailBox result = new LinearQuestDetailBox(new ScreenRectangle(left, top, partWidth(part), height), pinned, naturalHeight,
                flowTop);
        boxes.put(part, result);
        resolving.remove(part);
        return result;
    }

    private int naturalHeight(LinearQuestDetailPart part) {
        return switch (part) {
            case TITLE -> Mth.ceil(title.size() * lineHeight() * titleScale());
            case DESCRIPTION -> description.size() * lineHeight();
            case OBJECTIVES_HEADER, REWARDS_HEADER -> isHeaderVisible(part) ? font.lineHeight : 0;
            case OBJECTIVES_LIST -> actions.isEmpty() ? lineHeight() : objectiveOffsets[actions.size()];
            case REWARDS_LIST -> rewardIcons.isEmpty() ? lineHeight()
                    : (Mth.positiveCeilDiv(rewardIcons.size(), rewardColumns) - 1) * rewardStep() + ICON + REWARD_PADDING;
        };
    }

    private boolean isHeaderVisible(LinearQuestDetailPart part) {
        LinearQuestDetailPlacement placement = placements.get(part);
        return !part.isHeader() || placement == null || placement.showHeader();
    }

    private int partWidth(LinearQuestDetailPart part) {
        LinearQuestDetailPlacement placement = placements.get(part);
        return placement == null ? Math.max(1, getWidth() - totalInnerPadding()) : placement.bounds().width();
    }

    private float titleScale() {
        LinearQuestDetailPlacement placement = placements.get(LinearQuestDetailPart.TITLE);
        return placement == null ? TITLE_SCALE : placement.textScale();
    }

    private int objectiveGap() {
        LinearQuestDetailPlacement placement = placements.get(LinearQuestDetailPart.OBJECTIVES_LIST);
        return placement == null ? 7 : placement.spacing();
    }

    private int rewardStep() {
        LinearQuestDetailPlacement placement = placements.get(LinearQuestDetailPart.REWARDS_LIST);
        return placement == null ? REWARD_STEP : ICON + placement.spacing();
    }

    @Override
    protected void renderBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        // The scroll widget moves the entire canvas. Apply the scroll to the flowing parts only.
        graphics.pose().translate(0, scrollAmount(), 0);
        try {
            if (quest == null) {
                drawLines(graphics, font.split(getMessage(), partWidth(LinearQuestDetailPart.DESCRIPTION)),
                        getX() + innerPadding(), getY() + innerPadding(), partWidth(LinearQuestDetailPart.DESCRIPTION),
                        LinearQuestTextAlignment.LEFT, 0xFFBBBBBB);
                return;
            }
            LinearQuestDetailPart hovered = inside(mouseX, mouseY) ? interactivePartAt(mouseX, mouseY) : null;
            for (LinearQuestDetailPart part : PARTS) {
                if (!boxes.get(part).pinned()) {
                    renderPart(graphics, part, hovered, mouseX, mouseY);
                }
            }
            for (LinearQuestDetailPart part : PARTS) {
                if (boxes.get(part).pinned()) {
                    renderPart(graphics, part, hovered, mouseX, mouseY);
                }
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderPart(GuiGraphics graphics, LinearQuestDetailPart part, LinearQuestDetailPart hovered, int mouseX, int mouseY) {
        LinearQuestDetailBox box = boxes.get(part);
        if (box.bounds().height() <= 0) {
            return;
        }
        int x = partX(part);
        int y = partY(part);
        int width = box.bounds().width();
        if (placements.isEmpty()) {
            drawPart(graphics, part, hovered, mouseX, mouseY, x, y, width);
            return;
        }
        int clipTop = box.pinned() ? getY() + box.bounds().top() : y;
        ScreenRectangle region = new ScreenRectangle(x, clipTop, width, box.bounds().height());
        ScreenRectangle viewport = new ScreenRectangle(getX(), getY() + box.flowTop(), getWidth(),
                Math.max(0, getHeight() - box.flowTop()));
        ScreenRectangle visible = region.intersection(viewport);
        if (visible == null) {
            return;
        }
        List<ScreenRectangle> regions = new ArrayList<>();
        regions.add(visible);
        if (!box.pinned()) {
            // A fixed title or sidebar should not have scrolling text drawn through it.
            for (LinearQuestDetailBox fixed : boxes.values()) {
                if (fixed.pinned() && fixed.bounds().height() > 0) {
                    ScreenRectangle bounds = fixed.bounds();
                    cutOut(regions, new ScreenRectangle(getX() + bounds.left(), getY() + bounds.top(), bounds.width(), bounds.height()));
                }
            }
        }
        for (ScreenRectangle clip : regions) {
            graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
            try {
                drawPart(graphics, part, hovered, mouseX, mouseY, x, y, width);
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private void cutOut(List<ScreenRectangle> regions, ScreenRectangle obstacle) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            ScreenRectangle bounds = regions.get(i);
            ScreenRectangle overlap = bounds.intersection(obstacle);
            if (overlap != null) {
                regions.remove(i);
                addRegion(regions, bounds.left(), bounds.top(), bounds.right(), overlap.top());
                addRegion(regions, bounds.left(), overlap.bottom(), bounds.right(), bounds.bottom());
                addRegion(regions, bounds.left(), overlap.top(), overlap.left(), overlap.bottom());
                addRegion(regions, overlap.right(), overlap.top(), bounds.right(), overlap.bottom());
            }
        }
    }

    private void addRegion(List<ScreenRectangle> regions, int left, int top, int right, int bottom) {
        if (right > left && bottom > top) {
            regions.add(new ScreenRectangle(left, top, right - left, bottom - top));
        }
    }

    private void drawPart(GuiGraphics graphics, LinearQuestDetailPart part, LinearQuestDetailPart hovered,
                          int mouseX, int mouseY, int x, int y, int width) {
        switch (part) {
            case TITLE -> renderTitle(graphics, part, x, y, width);
            case DESCRIPTION -> drawLines(graphics, description, x, y, width,
                    alignment(part, LinearQuestTextAlignment.LEFT), 0xFFCCCCCC);
            case OBJECTIVES_HEADER, REWARDS_HEADER -> renderHeading(graphics, part, x, y, width);
            case OBJECTIVES_LIST -> renderObjectives(graphics, x, y, width, hovered == part ? objectiveAt(mouseY) : -1);
            case REWARDS_LIST -> renderRewards(graphics, x, y, width, hovered == part ? rewardAt(mouseX, mouseY) : -1);
        }
    }

    private void renderTitle(GuiGraphics graphics, LinearQuestDetailPart part, int x, int y, int width) {
        LinearQuestTextAlignment alignment = alignment(part, LinearQuestTextAlignment.CENTER);
        float scale = titleScale();
        int unscaledWidth = (int) (width / scale);
        graphics.pose().pushPose();
        if (alignment == LinearQuestTextAlignment.CENTER) {
            graphics.pose().translate(x + width / 2F, y, 0);
        } else {
            graphics.pose().translate(x, y, 0);
        }
        graphics.pose().scale(scale, scale, 1);
        for (int i = 0; i < title.size(); i++) {
            int textWidth = font.width(title.get(i));
            int offset = alignment == LinearQuestTextAlignment.CENTER ? -textWidth / 2 : alignment.offset(unscaledWidth, textWidth);
            graphics.drawString(font, title.get(i), offset, i * lineHeight(), 0xFFFFFFFF);
        }
        graphics.pose().popPose();
    }

    private LinearQuestTextAlignment alignment(LinearQuestDetailPart part, LinearQuestTextAlignment fallback) {
        LinearQuestDetailPlacement placement = placements.get(part);
        return placement == null ? fallback : placement.alignment();
    }

    private Component headingLabel(LinearQuestDetailPart part) {
        LinearQuestDetailPlacement placement = placements.get(part);
        if (placement != null && placement.label() != null) {
            return placement.label();
        }
        return part == LinearQuestDetailPart.OBJECTIVES_HEADER ? OBJECTIVES : REWARDS;
    }

    private void renderHeading(GuiGraphics graphics, LinearQuestDetailPart part, int x, int y, int width) {
        LinearQuestDetailPlacement placement = placements.get(part);
        LinearQuestSprites.heading(graphics, font, headingLabel(part), x, y, width,
                placement == null || placement.showDecoration(), placement == null ? null : placement.decoration());
    }

    private void renderObjectives(GuiGraphics graphics, int x, int top, int width, int hoveredObjective) {
        if (actions.isEmpty()) {
            graphics.drawCenteredString(font, NONE, x + width / 2, top, 0xFFAAAAAA);
        }
        for (int i = 0; i < actions.size(); i++) {
            int y = top + objectiveOffsets[i];
            if (!visibleRow(y, top + objectiveOffsets[i + 1])) {
                continue;
            }
            QuestObjective<?, ?> objective = quest.objectives().get(i);
            RenderAnimation animation = objectiveInsets.computeIfAbsent(objective.getObjectiveId(), id -> new RenderAnimation(0));
            ItemStack icon = objectiveIcons.get(i);
            List<FormattedCharSequence> lines = actions.get(i);
            boolean inset = hoveredObjective == i || (isFocused() && keyboardAction == i);
            ClientRenderUtil.renderInset(graphics.pose(), animation, inset, OBJECTIVE_INSET, INSET_DURATION_MS, amount -> {
                if (objective instanceof ObjectiveAcknowledge) {
                    QuestWidgetSprites.blitAcknowledgement(graphics, x, y,
                            ClientQuestData.getUser() != null && ClientQuestData.getUser().hasCompletedObjective(objective));
                } else {
                    graphics.renderItem(icon, x, y);
                }
                drawLines(graphics, lines, x + ICON + 8, y + 3, width, LinearQuestTextAlignment.LEFT, 0xFFDDDDDD);
            });
            Component progress = objective.getProgressText(ClientQuestData.getUser());
            int progressY = progressBelow ? y + Math.max(ICON, lines.size() * lineHeight() + 3) + 2 : y + 3;
            graphics.drawString(font, progress, x + width - font.width(progress), progressY, 0xFFCCCCCC);
        }
    }

    private void renderRewards(GuiGraphics graphics, int left, int top, int width, int hoveredReward) {
        if (rewardIcons.isEmpty()) {
            graphics.drawCenteredString(font, NONE, left + width / 2, top, 0xFFAAAAAA);
        }
        for (int i = 0; i < rewardIcons.size(); i++) {
            int x = rewardX(i);
            int y = top + i / rewardColumns * rewardStep();
            if (!visibleRow(y - REWARD_PADDING, y + ICON + REWARD_PADDING)) {
                continue;
            }
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
        ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        profiler.push("quest_details");
        try {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (quest != null && inside(mouseX, mouseY)) {
                renderHover(graphics, mouseX, mouseY);
            }
        } finally {
            profiler.pop();
        }
    }

    private void renderHover(GuiGraphics graphics, int mouseX, int mouseY) {
        LinearQuestDetailPart hovered = interactivePartAt(mouseX, mouseY);
        int reward = hovered == LinearQuestDetailPart.REWARDS_LIST ? rewardAt(mouseX, mouseY) : -1;
        if (reward >= 0) {
            RewardType<?> value = quest.rewards().get(reward);
            if (value instanceof RewardItem item) {
                ItemStack stack = item.getStack();
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
                lines.add(Component.translatable("screen.vuequests.reward.quantity", stack.getCount()).withStyle(ChatFormatting.GRAY));
                graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, value.getDisplayName(), mouseX, mouseY);
            }
        } else if (hovered == LinearQuestDetailPart.DESCRIPTION) {
            Style style = styleAt(mouseX, mouseY);
            if (style != null) {
                graphics.renderComponentHoverEffect(font, style, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (quest == null || button != 0 || !inside(mouseX, mouseY)) {
            return false;
        }
        LinearQuestDetailPart hovered = interactivePartAt(mouseX, mouseY);
        int action = switch (hovered) {
            case OBJECTIVES_LIST -> objectiveAt(mouseY);
            case REWARDS_LIST -> {
                int reward = rewardAt(mouseX, mouseY);
                yield reward < 0 ? -1 : actions.size() + reward;
            }
            case null, default -> -1;
        };
        if (action >= 0) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            keyboardAction = action;
            interact(action);
            return true;
        }
        Style style = hovered == LinearQuestDetailPart.DESCRIPTION ? styleAt(mouseX, mouseY) : null;
        if (style != null && onTextClick.test(style)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (quest != null && inside(mouseX, mouseY) && scrollY != 0) {
            LinearQuestDetailPart part = interactivePartAt(mouseX, mouseY);
            if (part != null && boxes.get(part).maxScroll() > 0) {
                int previous = partScroll.getOrDefault(part, 0);
                int next = Mth.clamp(previous - Mth.sign(scrollY) * Mth.ceil(Math.abs(scrollY) * scrollRate()),
                        0, boxes.get(part).maxScroll());
                if (previous != next) {
                    partScroll.put(part, next);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void interact(int index) {
        if (index < 0 || !ClientQuestData.prerequisitesMet(quest)) {
            return;
        }
        if (index < actions.size()) {
            QuestObjective<?, ?> objective = quest.objectives().get(index);
            if (objective.requiresInteraction() && ClientQuestData.getUser() != null
                    && !ClientQuestData.getUser().hasCompletedObjective(objective)) {
                onObjectiveClick.accept(objective);
            }
        } else {
            RewardType<?> reward = quest.rewards().get(index - actions.size());
            if (ClientQuestData.canClaimRewards(quest) && !ClientQuestData.isRewardClaimed(reward)) {
                onRewardClick.accept(reward);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (quest == null) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> setScrollAmount(scrollAmount() - 22);
            case GLFW.GLFW_KEY_DOWN -> setScrollAmount(scrollAmount() + 22);
            case GLFW.GLFW_KEY_PAGE_UP -> setScrollAmount(scrollAmount() - getHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> setScrollAmount(scrollAmount() + getHeight());
            case GLFW.GLFW_KEY_HOME -> setScrollAmount(0);
            case GLFW.GLFW_KEY_END -> setScrollAmount(getMaxScrollAmount());
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                int count = actions.size() + rewardIcons.size();
                if (count == 0) {
                    return false;
                }
                keyboardAction = Math.floorMod(keyboardAction + (keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1), count);
                revealAction();
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> interact(keyboardAction);
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    private void revealAction() {
        boolean objective = keyboardAction < actions.size();
        LinearQuestDetailPart part = objective ? LinearQuestDetailPart.OBJECTIVES_LIST : LinearQuestDetailPart.REWARDS_LIST;
        LinearQuestDetailBox box = boxes.get(part);
        int y = objective ? objectiveOffsets[keyboardAction] : (keyboardAction - actions.size()) / rewardColumns * rewardStep();
        if (box.pinned()) {
            int scroll = partScroll.getOrDefault(part, 0);
            partScroll.put(part, Mth.clamp(revealOffset(scroll, y, 0, box.bounds().height()), 0, box.maxScroll()));
        } else {
            y += box.bounds().top() - innerPadding();
            int topInset = Math.max(0, box.flowTop() - innerPadding());
            setScrollAmount(revealOffset((int) scrollAmount(), y, topInset, height - totalInnerPadding()));
        }
    }

    private int revealOffset(int scroll, int y, int topInset, int viewportHeight) {
        if (y < scroll + topInset) {
            return y - topInset;
        }
        return Math.max(scroll, y + ICON - viewportHeight);
    }

    private LinearQuestDetailPart interactivePartAt(double mouseX, double mouseY) {
        if (placements.isEmpty()) {
            if (objectiveAt(mouseY) >= 0) {
                return LinearQuestDetailPart.OBJECTIVES_LIST;
            }
            return rewardAt(mouseX, mouseY) >= 0 ? LinearQuestDetailPart.REWARDS_LIST : LinearQuestDetailPart.DESCRIPTION;
        }
        for (int i = PARTS.length - 1; i >= 0; i--) {
            LinearQuestDetailPart part = PARTS[i];
            if (boxes.get(part).pinned() && contains(part, mouseX, mouseY)) {
                return part;
            }
        }
        for (int i = PARTS.length - 1; i >= 0; i--) {
            LinearQuestDetailPart part = PARTS[i];
            if (!boxes.get(part).pinned() && contains(part, mouseX, mouseY)) {
                return part;
            }
        }
        return null;
    }

    private boolean contains(LinearQuestDetailPart part, double mouseX, double mouseY) {
        LinearQuestDetailBox box = boxes.get(part);
        int x = partX(part);
        int y = box.pinned() ? getY() + box.bounds().top() : partY(part);
        return mouseX >= x && mouseX < x + box.bounds().width()
                && mouseY >= Math.max(y, getY() + box.flowTop()) && mouseY < y + box.bounds().height();
    }

    private int objectiveAt(double mouseY) {
        double y = mouseY - partY(LinearQuestDetailPart.OBJECTIVES_LIST);
        for (int i = 0; i < actions.size(); i++) {
            if (y >= objectiveOffsets[i] && y < objectiveOffsets[i + 1] - objectiveGap()) {
                return i;
            }
        }
        return -1;
    }

    private int rewardAt(double mouseX, double mouseY) {
        double y = mouseY - partY(LinearQuestDetailPart.REWARDS_LIST);
        if (y < -REWARD_PADDING) {
            return -1;
        }
        int step = rewardStep();
        int row = (int) ((y + REWARD_PADDING) / step);
        if (y - row * step >= ICON + REWARD_PADDING) {
            return -1;
        }
        int first = row * rewardColumns;
        for (int i = first; i < Math.min(first + rewardColumns, rewardIcons.size()); i++) {
            if (mouseX >= rewardX(i) - REWARD_PADDING && mouseX < rewardX(i) + ICON + REWARD_PADDING) {
                return i;
            }
        }
        return -1;
    }

    private int rewardX(int index) {
        int step = rewardStep();
        int first = index / rewardColumns * rewardColumns;
        int count = Math.min(rewardColumns, rewardIcons.size() - first);
        LinearQuestDetailPart part = LinearQuestDetailPart.REWARDS_LIST;
        return partX(part) + (partWidth(part) - ((count - 1) * step + ICON)) / 2 + (index - first) * step;
    }

    private Style styleAt(double mouseX, double mouseY) {
        LinearQuestDetailPart part = LinearQuestDetailPart.DESCRIPTION;
        double y = mouseY - partY(part);
        int index = (int) (y / lineHeight());
        if (y < 0 || index >= description.size() || y % lineHeight() >= font.lineHeight) {
            return null;
        }
        FormattedCharSequence line = description.get(index);
        int textWidth = font.width(line);
        double x = mouseX - partX(part) - alignment(part, LinearQuestTextAlignment.LEFT).offset(partWidth(part), textWidth);
        return x < 0 || x >= textWidth ? null : font.getSplitter().componentStyleAtWidth(line, (int) x);
    }

    private boolean inside(double x, double y) {
        return active && visible && x >= getX() + 1 && x < getRight() - 1 && y >= getY() + 1 && y < getBottom() - 1;
    }

    private boolean visibleRow(int top, int bottom) {
        return bottom >= getY() && top <= getBottom();
    }

    private void drawLines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int y, int width,
                           LinearQuestTextAlignment alignment, int color) {
        for (FormattedCharSequence line : lines) {
            if (visibleRow(y, y + lineHeight())) {
                int offset = alignment == LinearQuestTextAlignment.LEFT ? 0 : alignment.offset(width, font.width(line));
                graphics.drawString(font, line, x + offset, y, color);
            }
            y += lineHeight();
        }
    }

    private int partX(LinearQuestDetailPart part) {
        return getX() + boxes.get(part).bounds().left();
    }

    private int partY(LinearQuestDetailPart part) {
        LinearQuestDetailBox box = boxes.get(part);
        int scroll = box.pinned() ? partScroll.getOrDefault(part, 0) : (int) scrollAmount();
        return getY() + box.bounds().top() - scroll;
    }

    private int lineHeight() {
        return font.lineHeight + 2;
    }

    @Override
    protected int getInnerHeight() {
        return contentHeight;
    }

    @Override
    protected boolean scrollbarVisible() {
        return false;
    }

    @Override
    protected double scrollRate() {
        return 22;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (quest == null) {
            return;
        }
        List<Component> contents = new ArrayList<>();
        contents.add(quest.display().description());
        if (isHeaderVisible(LinearQuestDetailPart.OBJECTIVES_HEADER)) {
            contents.add(headingLabel(LinearQuestDetailPart.OBJECTIVES_HEADER));
        }
        quest.objectives().forEach(objective -> contents.add(Component.empty().append(objective.getActionText())
                .append(" ").append(objective.getProgressText(ClientQuestData.getUser()))));
        if (isHeaderVisible(LinearQuestDetailPart.REWARDS_HEADER)) {
            contents.add(headingLabel(LinearQuestDetailPart.REWARDS_HEADER));
        }
        quest.rewards().forEach(reward -> contents.add(reward.getDisplayName()));
        output.add(NarratedElementType.HINT, contents.toArray(Component[]::new));
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.linear_details_usage"));
    }
}
