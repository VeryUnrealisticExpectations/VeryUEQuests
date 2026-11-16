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

public record InviteTeamPlayerPayload(UUID requestId, UUID teamId, String targetName) implements TeamRequestPayload {
    public static final Type<InviteTeamPlayerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "invite_team_player"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InviteTeamPlayerPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InviteTeamPlayerPayload::requestId,
            UUIDUtil.STREAM_CODEC, InviteTeamPlayerPayload::teamId,
            ByteBufCodecs.stringUtf8(16), InviteTeamPlayerPayload::targetName,
            InviteTeamPlayerPayload::new);

    @Override
    public Set<UUID> apply(UserManager manager, ServerPlayer player) { return manager.invite(player.getUUID(), teamId, targetName); }
    @Override
    public Type<InviteTeamPlayerPayload> type() { return TYPE; }
}
