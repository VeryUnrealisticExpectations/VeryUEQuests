package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ObjectiveStatistic extends CountedQuestObjective<Stat<?>> {
    public static final MapCodec<Stat<?>> STAT_CODEC = BuiltInRegistries.STAT_TYPE.byNameCodec()
            .dispatchMap("stat_type", Stat::getType, ObjectiveStatistic::statValueCodec);
    public static final MapCodec<ObjectiveStatistic> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("objective_id").forGetter(ObjectiveStatistic::getObjectiveId),
            STAT_CODEC.forGetter(ObjectiveStatistic::getValue),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ObjectiveStatistic::getCount)
    ).apply(instance, ObjectiveStatistic::new));

    private static <T> MapCodec<Stat<T>> statValueCodec(StatType<T> type) {
        return type.getRegistry().byNameCodec().xmap(type::get, Stat::getValue).fieldOf("statistic");
    }

    public ObjectiveStatistic(UUID objectiveId, Stat<?> statistic, int count) {
        super(objectiveId, statistic, count);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    @Override
    public Component getActionText() {
        return Component.translatable("screen.vuequests.objective.statistic", getCount(), getValue().getName());
    }

    @Override
    public void incrementObjectiveProgress(User user, QuestObjectiveProgress<Stat<?>, Integer> progress, ObjectiveContext context) {
        ServerPlayer player = context.getParamOrNull(ObjectiveContext.PLAYER);
        Stat<?> changed = context.getParamOrNull(ObjectiveContext.STATISTIC);
        if (player != null && (changed == null || changed.equals(getValue()))) {
            progress.setCurrentProgress(Math.max(getProgressCount(progress.getCurrentProgress()), Math.min(getCount(), player.getStats().getValue(getValue()))));
        }
    }

    @Override
    public QuestObjectiveSerializer<Integer> getType() {
        return QuestObjectiveTypes.STATISTIC;
    }
}
