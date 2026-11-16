package dev.veryue.vuequests.network;

import net.minecraft.core.UUIDUtil;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record TeamActionResultPayload(UUID requestId, boolean success, Component message) implements CustomPacketPayload {
    public static final Type<TeamActionResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "team_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamActionResultPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TeamActionResultPayload::requestId,
            ByteBufCodecs.BOOL, TeamActionResultPayload::success,
            ComponentSerialization.TRUSTED_STREAM_CODEC, TeamActionResultPayload::message,
            TeamActionResultPayload::new);
    @Override
    public Type<TeamActionResultPayload> type() { return TYPE; }
}
