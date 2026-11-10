package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.Codec;
import dev.veryue.vuequests.VeryUERegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class RewardType<T> {

    public static final Codec<RewardType<?>> CODEC = Codec.lazyInitialized(() -> VeryUERegistries.REWARD_TYPES
            .byNameCodec()
            .dispatch("type", RewardType::getType, RewardTypeSerializer::codec));

    private final UUID rewardId;
    private final T value;

    public RewardType(UUID rewardId, T value) {
        this.rewardId = rewardId;
        this.value = value;
    }

    public UUID getRewardId() {
        return rewardId;
    }

    public T getValue() {
        return value;
    }

    public ItemStack getIconStack() {
        return new ItemStack(Items.PAPER);
    }

    /**
     * @return The icon label will control the text that appears in the 'rewards' section.
     */
    @Nullable
    public Component getIconLabel() {
        return null;
    }

    public Component getDisplayName() {
        return Component.literal(String.valueOf(VeryUERegistries.REWARD_TYPES.getKey(getType())));
    }

    public abstract RewardTypeSerializer getType();

    public abstract boolean tryGiveReward(ServerPlayer player);
}
