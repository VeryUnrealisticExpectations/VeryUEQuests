package dev.veryue.vuequests.client.widget;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.util.ClientRenderUtil;
import dev.veryue.vuequests.client.util.RenderAnimation;
import dev.veryue.vuequests.models.quest.Quest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class LinearQuestList extends AbstractWidget {

    private static final int ROW_HEIGHT = 22;
    private static final int HEADING_HEIGHT = 20;
    private static final int GROUP_GAP = 12;
    private static final float INSET = 6;
    private static final long INSET_DURATION_MS = 160;
    private final Font font;
    private final Consumer<Quest> onSelect;
    private List<Quest> entries = List.of();
    private List<ItemStack> icons = List.of();
    private int availableCount;
    private UUID selected;
    private double scroll;
    private final Map<UUID, RenderAnimation> rowInsets = new HashMap<>();

    public LinearQuestList(Font font, Consumer<Quest> onSelect) {
        super(0, 0, 1, 1, Component.translatable("screen.vuequests.quest_menu"));
        this.font = font;
        this.onSelect = onSelect;
    }

    public void setQuests(List<Quest> quests) {
        Comparator<Quest> order = Comparator.comparing((Quest quest) -> quest.display().title().getString(),
                String.CASE_INSENSITIVE_ORDER).thenComparing(Quest::questId);
        List<Quest> available = quests.stream().filter(quest -> !ClientQuestData.isQuestComplete(quest)
                && ClientQuestData.prerequisitesMet(quest)).sorted(order).toList();
        List<Quest> completed = quests.stream().filter(ClientQuestData::isQuestComplete).sorted(order).toList();
        List<Quest> next = new ArrayList<>(available);
        next.addAll(completed);
        availableCount = available.size();
        entries = next;
        rowInsets.keySet().retainAll(entries.stream().map(Quest::questId).collect(Collectors.toSet()));
        icons = entries.stream().map(quest -> new ItemStack(quest.display().icon())).toList();
        Quest selection = entries.stream().filter(quest -> quest.questId().equals(selected)).findFirst()
                .orElse(entries.isEmpty() ? null : entries.getFirst());
        select(selection, false);
        clampScroll();
    }

    private void select(Quest quest, boolean reveal) {
        selected = quest == null ? null : quest.questId();
        setMessage(quest == null ? Component.translatable("screen.vuequests.no_available_quests") : quest.display().title());
        onSelect.accept(quest);
        if (reveal && quest != null) {
            int top = rowTop(entries.indexOf(quest));
            if (top < scroll) scroll = top;
            if (top + ROW_HEIGHT > scroll + height) scroll = top + ROW_HEIGHT - height;
            clampScroll();
        }
    }

    private int rowTop(int index) {
        return HEADING_HEIGHT + index * ROW_HEIGHT
                + (index >= availableCount ? HEADING_HEIGHT + GROUP_GAP : 0);
    }

    private int contentHeight() {
        return 2 * HEADING_HEIGHT + GROUP_GAP + entries.size() * ROW_HEIGHT;
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight() - height));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("quest_list");
        try {
            renderRows(graphics, mouseX, mouseY);
        } finally {
            profiler.pop();
        }
    }

    private void renderRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        clampScroll();
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        int top = getY() - (int) scroll;
        graphics.text(font, Component.translatable("screen.vuequests.available"), getX() + 3, top + 3, 0xFFE0E0E0);
        int completedTop = top + HEADING_HEIGHT + availableCount * ROW_HEIGHT + GROUP_GAP;
        graphics.text(font, Component.translatable("screen.vuequests.completed"), getX() + 3, completedTop + 3, 0xFFA0A0A0);
        for (int i = 0; i < entries.size(); i++) {
            int y = top + rowTop(i);
            if (y + ROW_HEIGHT <= getY() || y >= getBottom()) continue;
            Quest quest = entries.get(i);
            boolean current = quest.questId().equals(selected);
            boolean hovered = isMouseOver(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_HEIGHT;
            RenderAnimation animation = rowInsets.computeIfAbsent(quest.questId(), id -> new RenderAnimation(current ? INSET : 0));
            ItemStack icon = icons.get(i);
            boolean completed = i >= availableCount;
            ClientRenderUtil.renderInset(graphics.pose(), animation, current || hovered, INSET, INSET_DURATION_MS, inset -> {
                graphics.item(icon, getX() + 3, y + 3);
                if (completed) QuestWidgetSprites.blitClaimBadge(graphics, getX() + 3, y + 3, true);
                Component styledTitle = quest.display().title().copy().withStyle(style -> style.withBold(current));
                var title = Language.getInstance().getVisualOrder(font.substrByWidth(styledTitle, Math.max(1, width - 27 - Mth.ceil(inset))));
                graphics.text(font, title, getX() + 25, y + 7,
                        current ? 0xFFFFFFFF : completed ? 0xFF999999 : 0xFFCCCCCC);
            });
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!active || !visible || button != InputConstants.MOUSE_BUTTON_LEFT || !isMouseOver(mouseX, mouseY)) return false;
        double y = mouseY - getY() + scroll;
        for (int i = 0; i < entries.size(); i++) {
            if (y >= rowTop(i) && y < rowTop(i) + ROW_HEIGHT) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                select(entries.get(i), false);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scroll -= deltaY * ROW_HEIGHT;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        if (entries.isEmpty()) return false;
        int current = 0;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).questId().equals(selected)) current = i;
        int next = switch (keyCode) {
            case InputConstants.KEY_UP -> current - 1;
            case InputConstants.KEY_DOWN -> current + 1;
            case InputConstants.KEY_HOME -> 0;
            case InputConstants.KEY_END -> entries.size() - 1;
            case InputConstants.KEY_PAGEUP -> current - Math.max(1, height / ROW_HEIGHT);
            case InputConstants.KEY_PAGEDOWN -> current + Math.max(1, height / ROW_HEIGHT);
            default -> Integer.MIN_VALUE;
        };
        if (next == Integer.MIN_VALUE) return super.keyPressed(event);
        select(entries.get(Mth.clamp(next, 0, entries.size() - 1)), true);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("narration.vuequests.linear_list_usage"));
    }
}
