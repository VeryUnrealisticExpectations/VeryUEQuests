package dev.veryue.vuequests.network;

import dev.veryue.vuequests.UserManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;
import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record KickTeamMemberPayload(UUID requestId, UUID teamId, UUID memberId) implements TeamRequestPayload {
    public static final Type<KickTeamMemberPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "kick_team_member"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KickTeamMemberPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, KickTeamMemberPayload::requestId,
            UUIDUtil.STREAM_CODEC, KickTeamMemberPayload::teamId,
            UUIDUtil.STREAM_CODEC, KickTeamMemberPayload::memberId,
            KickTeamMemberPayload::new);

    @Override
    public Set<UUID> apply(UserManager manager, ServerPlayer player) { return manager.kickMember(player.getUUID(), teamId, memberId); }
    @Override
    public Type<KickTeamMemberPayload> type() { return TYPE; }
}
