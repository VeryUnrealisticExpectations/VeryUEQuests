package dev.veryue.vuequests.network;

import dev.veryue.vuequests.config.QuestView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record QuestViewSyncPayload(QuestView view) implements CustomPacketPayload {

    public static final Type<QuestViewSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "quest_view_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestViewSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeEnum(payload.view()),
            buffer -> new QuestViewSyncPayload(buffer.readEnum(QuestView.class)));

    @Override
    public Type<QuestViewSyncPayload> type() {
        return TYPE;
    }
}
