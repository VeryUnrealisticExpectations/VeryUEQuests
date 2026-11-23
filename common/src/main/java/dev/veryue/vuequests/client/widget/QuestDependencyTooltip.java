package dev.veryue.vuequests.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuestDependencyTooltip {

    private static final Component HEADING = Component.translatable("screen.vuequests.dependencies").withStyle(ChatFormatting.BOLD);
    private static final int MAX_WIDTH = 240;
    private static final int HEADING_GAP = 2;

    private final List<Component> entries = new ArrayList<>();

    public QuestDependencyTooltip(Quest quest) {
        QuestData data = ClientQuestData.getTranslatedData();
        for (UUID dependencyId : quest.dependencies()) {
            Quest dependency = data.quests().get(dependencyId);
            if (dependency != null && !quest.sectionUUID().equals(dependency.sectionUUID())) {
                Section section = data.sections().get(dependency.sectionUUID());
                if (section != null) {
                    entries.add(Component.translatable("screen.vuequests.dependency_entry", section.name(), dependency.display().title()));
                }
            }
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int wrapWidth = Math.max(1, Math.min(MAX_WIDTH, graphics.guiWidth() - 8));
        List<FormattedCharSequence> headingLines = font.split(HEADING, wrapWidth);
        List<FormattedCharSequence> entryLines = entries.stream().flatMap(entry -> font.split(entry, wrapWidth).stream()).toList();
        int width = headingLines.stream().mapToInt(font::width).max().orElse(0);
        for (FormattedCharSequence line : entryLines) {
            width = Math.max(width, font.width(line));
        }
        int height = (headingLines.size() + entryLines.size()) * font.lineHeight + HEADING_GAP;
        Vector2ic position = DefaultTooltipPositioner.INSTANCE.positionTooltip(graphics.guiWidth(), graphics.guiHeight(),
                mouseX, mouseY, width + 4, height + 1);
        int x = position.x();
        int y = Math.max(4, position.y());
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(0, 0, 400);
            TooltipRenderUtil.renderTooltipBackground(graphics, x, y, width, height, 0);
            for (FormattedCharSequence line : headingLines) {
                graphics.drawString(font, line, x + (width - font.width(line)) / 2, y, 0xFFFFFFFF);
                y += font.lineHeight;
            }
            y += HEADING_GAP;
            for (FormattedCharSequence line : entryLines) {
                graphics.drawString(font, line, x, y, 0xFFFFFFFF);
                y += font.lineHeight;
            }
        } finally {
            pose.popPose();
        }
    }
}
