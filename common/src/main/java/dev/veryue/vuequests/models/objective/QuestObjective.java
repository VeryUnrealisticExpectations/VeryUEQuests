package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.Codec;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        int target = Math.max(1, getProgressTarget());
        QuestObjectiveProgress<T, TYPE> progress = user == null ? null : user.getObjectiveProgress(this);
        boolean complete = user != null && (user.hasCompletedObjective(this) || (progress != null && progress.isComplete(this)));
        int count = progress == null ? 0 : getProgressCount(progress.getCurrentProgress());
        int displayedCount = complete ? target : Mth.clamp(count, 0, target);
        return Component.literal(displayedCount + "/" + target).withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    protected int getProgressTarget() {
        return 1;
    }

    protected int getProgressCount(@Nullable TYPE progress) {
        return 0;
    }

    public T getValue() {
        return value;
    }

    public abstract void incrementObjectiveProgress(User user, QuestObjectiveProgress<T, TYPE> objectiveProgress, ObjectiveContext context);

    public abstract boolean isComplete(QuestObjectiveProgress<T, TYPE> progress);

    public abstract QuestObjectiveSerializer<TYPE> getType();
}
