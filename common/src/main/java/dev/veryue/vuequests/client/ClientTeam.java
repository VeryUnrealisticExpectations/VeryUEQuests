package dev.veryue.vuequests.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

public record ClientTeam(UUID id, String name, UUID ownerId, List<GameProfile> members,
                         boolean sharingProgressWithTeamMembers, boolean keepProgressWhenLeavingTeam) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientTeam> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ClientTeam::id,
            ByteBufCodecs.stringUtf8(64), ClientTeam::name,
            UUIDUtil.STREAM_CODEC, ClientTeam::ownerId,
            ByteBufCodecs.GAME_PROFILE.apply(ByteBufCodecs.list()), ClientTeam::members,
            ByteBufCodecs.BOOL, ClientTeam::sharingProgressWithTeamMembers,
            ByteBufCodecs.BOOL, ClientTeam::keepProgressWhenLeavingTeam,
            ClientTeam::new);

    public boolean isOwner(UUID playerId) {
        return ownerId.equals(playerId);
    }
}
