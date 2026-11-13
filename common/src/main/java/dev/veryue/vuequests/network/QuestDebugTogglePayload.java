package dev.veryue.vuequests.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record QuestDebugTogglePayload() implements CustomPacketPayload {

    public static final Type<QuestDebugTogglePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "quest_debug_toggle"));
    public static final QuestDebugTogglePayload INSTANCE = new QuestDebugTogglePayload();
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestDebugTogglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<QuestDebugTogglePayload> type() {
        return TYPE;
    }
}
