package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.Codec;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.VeryUERegistries;

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

    public abstract RewardTypeSerializer getType();

    public abstract void giveReward(User user); // todo; not sure.
}
