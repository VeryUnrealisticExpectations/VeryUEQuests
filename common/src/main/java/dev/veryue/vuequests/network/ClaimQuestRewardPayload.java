package dev.veryue.vuequests.network;

import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record ClaimQuestRewardPayload(UUID questId, UUID rewardId) implements CustomPacketPayload {

    public static final Type<ClaimQuestRewardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "claim_quest_reward"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimQuestRewardPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ClaimQuestRewardPayload::questId,
            UUIDUtil.STREAM_CODEC, ClaimQuestRewardPayload::rewardId,
            ClaimQuestRewardPayload::new);

    @Override
    public Type<ClaimQuestRewardPayload> type() {
        return TYPE;
    }

    public boolean handle(ServerPlayer player) {
        return VeryUEMod.INSTANCE.getUser(player.getUUID())
                .map(user -> QuestReloadDelegate.INSTANCE.getData().tryClaimReward(user, player, questId, rewardId))
                .orElse(false);
    }
}
