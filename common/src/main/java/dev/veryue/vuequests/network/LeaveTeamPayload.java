package dev.veryue.vuequests.network;

import dev.veryue.vuequests.UserManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;
import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record LeaveTeamPayload(UUID requestId, UUID teamId) implements TeamRequestPayload {
    public static final Type<LeaveTeamPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "leave_team"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LeaveTeamPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LeaveTeamPayload::requestId,
            UUIDUtil.STREAM_CODEC, LeaveTeamPayload::teamId,
            LeaveTeamPayload::new);

    @Override
    public Set<UUID> apply(UserManager manager, ServerPlayer player) { return manager.leaveTeam(player.getUUID(), teamId); }
    @Override
    public Type<LeaveTeamPayload> type() { return TYPE; }
}
