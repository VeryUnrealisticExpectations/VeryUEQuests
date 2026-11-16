package dev.veryue.vuequests.network;

import dev.veryue.vuequests.UserManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;
import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record RespondTeamInvitationPayload(UUID requestId, UUID teamId, boolean accept) implements TeamRequestPayload {
    public static final Type<RespondTeamInvitationPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "respond_team_invitation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RespondTeamInvitationPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RespondTeamInvitationPayload::requestId,
            UUIDUtil.STREAM_CODEC, RespondTeamInvitationPayload::teamId,
            ByteBufCodecs.BOOL, RespondTeamInvitationPayload::accept,
            RespondTeamInvitationPayload::new);

    @Override
    public Set<UUID> apply(UserManager manager, ServerPlayer player) { return manager.respondToInvitation(player, teamId, accept); }
    @Override
    public Type<RespondTeamInvitationPayload> type() { return TYPE; }
}
