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

public record InteractQuestObjectivePayload(UUID questId, UUID objectiveId) implements CustomPacketPayload {
    public static final Type<InteractQuestObjectivePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "interact_quest_objective"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InteractQuestObjectivePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractQuestObjectivePayload::questId,
            UUIDUtil.STREAM_CODEC, InteractQuestObjectivePayload::objectiveId,
            InteractQuestObjectivePayload::new);

    public boolean handle(ServerPlayer player) {
        return VeryUEMod.INSTANCE.getUser(player.getUUID())
                .map(user -> QuestReloadDelegate.INSTANCE.getData().interactWithObjective(user, player, questId, objectiveId)).orElse(false);
    }

    @Override
    public Type<InteractQuestObjectivePayload> type() {
        return TYPE;
    }
}
