package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.Codec;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class QuestObjective<T, TYPE> {

    public static final Codec<QuestObjective<?, ?>> CODEC = Codec.lazyInitialized(() -> VeryUERegistries.QUEST_OBJECTIVE_TYPES
            .byNameCodec()
            .dispatch("type", QuestObjective::getType, QuestObjectiveSerializer::codec));

    private final UUID objectiveId;
    private final T value;

    public QuestObjective(UUID objectiveId, T value) {
        this.objectiveId = objectiveId;
        this.value = value;
    }

    @Deprecated
    public QuestObjective(UUID objectiveId, Item ignoredIcon, T value) {
        this(objectiveId, value);
    }

    public UUID getObjectiveId() {
        return objectiveId;
    }

    public Item getIcon() {
        return getIconStack().getItem();
    }

    public ItemStack getIconStack() {
        return new ItemStack(Items.PAPER);
    }

    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.generic", getIconStack().getHoverName());
    }

    public Component getProgressText(@Nullable User user) {
        return progressText(user, false);
    }

    public Component getIconLabel(@Nullable User user) {
        return progressText(user, true);
    }

    private Component progressText(@Nullable User user, boolean iconLabel) {
        QuestObjectiveProgress<T, TYPE> progress = user == null ? null : user.getObjectiveProgress(this);
        boolean complete = isComplete(user, progress);
        TYPE currentProgress = progress == null ? null : progress.getCurrentProgress();
        MutableComponent text = iconLabel ? formatIconLabel(currentProgress, complete) : formatProgress(currentProgress, complete);
        return text.withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    public double getProgressFraction(@Nullable User user) {
        QuestObjectiveProgress<T, TYPE> progress = user == null ? null : user.getObjectiveProgress(this);
        return isComplete(user, progress) ? 1 : Mth.clamp(formatProgressFraction(progress == null ? null : progress.getCurrentProgress()), 0, 1);
    }

    private boolean isComplete(@Nullable User user, @Nullable QuestObjectiveProgress<T, TYPE> progress) {
        return user != null && (user.hasCompletedObjective(this) || (progress != null && progress.isComplete(this)));
    }

    protected double formatProgressFraction(@Nullable TYPE progress) {
        return 0;
    }

    protected MutableComponent formatIconLabel(@Nullable TYPE progress, boolean complete) {
        return formatProgress(progress, complete);
    }

    protected MutableComponent formatProgress(@Nullable TYPE progress, boolean complete) {
        return Component.translatable(complete ? "screen.vuequests.progress.done" : "screen.vuequests.progress.pending");
    }

    public T getValue() {
        return value;
    }

    public abstract void incrementObjectiveProgress(User user, QuestObjectiveProgress<T, TYPE> objectiveProgress, ObjectiveContext context);

    public abstract boolean isComplete(QuestObjectiveProgress<T, TYPE> progress);

    public abstract QuestObjectiveSerializer<TYPE> getType();
}
