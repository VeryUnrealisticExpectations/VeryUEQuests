package dev.veryue.vuequests.network;

import com.mojang.authlib.GameProfile;
import dev.veryue.vuequests.UserManager;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.client.ClientTeam;
import dev.veryue.vuequests.client.ClientTeamInvitation;
import dev.veryue.vuequests.models.TeamMember;
import dev.veryue.vuequests.models.TeamUser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record TeamSyncPayload(Optional<ClientTeam> team,
                              List<ClientTeamInvitation> invitations) implements CustomPacketPayload {
    public static final Type<TeamSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "team_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ClientTeam.STREAM_CODEC.apply(ByteBufCodecs::optional), TeamSyncPayload::team,
            ClientTeamInvitation.STREAM_CODEC.apply(ByteBufCodecs.list()), TeamSyncPayload::invitations,
            TeamSyncPayload::new);

    public static TeamSyncPayload create(ServerPlayer recipient) {
        UserManager manager = VeryUEMod.INSTANCE.getUserManager();
        Optional<ClientTeam> team = manager.getPlayerTeam(recipient.getUUID()).map(source -> new ClientTeam(
                source.getUuid(), source.getName(), source.getOwnerId(),
                source.getMembers().keySet().stream().sorted().map(id -> profile(recipient, source, id)).toList(),
                source.isSharingProgressWithTeamMembers(), source.isKeepProgressWhenLeavingTeam()));
        List<ClientTeamInvitation> invitations = manager.getInvitations(recipient.getUUID()).stream().map(source ->
                new ClientTeamInvitation(source.getUuid(), source.getName(), profile(recipient, source, source.getOwnerId()))).toList();
        return new TeamSyncPayload(team, invitations);
    }

    private static GameProfile profile(ServerPlayer recipient, TeamUser team, UUID memberId) {
        TeamMember member = team.getMembers().get(memberId);
        ServerPlayer online = recipient.server.getPlayerList().getPlayer(memberId);
        GameProfile source = online == null ? null : online.getGameProfile();
        if (source == null && recipient.server.getProfileCache() != null) {
            source = recipient.server.getProfileCache().get(memberId).orElse(null);
        }
        GameProfile result = new GameProfile(memberId, online == null ? member.lastKnownName() : online.getGameProfile().getName());
        if (source != null) result.getProperties().putAll(source.getProperties());
        return result;
    }

    @Override
    public Type<TeamSyncPayload> type() {
        return TYPE;
    }
}
