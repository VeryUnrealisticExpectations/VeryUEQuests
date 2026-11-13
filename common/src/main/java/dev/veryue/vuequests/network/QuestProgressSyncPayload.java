package dev.veryue.vuequests.network;

import dev.veryue.vuequests.models.PlayerUser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record QuestProgressSyncPayload(PlayerUser user) implements CustomPacketPayload {

    public static final Type<QuestProgressSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "quest_progress_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestProgressSyncPayload> STREAM_CODEC = ByteBufCodecs
            .fromCodecWithRegistries(PlayerUser.CODEC)
            .map(QuestProgressSyncPayload::new, QuestProgressSyncPayload::user);

    public static QuestProgressSyncPayload create(ServerPlayer player) {
        return new QuestProgressSyncPayload(QuestSyncPayload.snapshotUser(player));
    }

    @Override
    public Type<QuestProgressSyncPayload> type() {
        return TYPE;
    }
}
