package dev.veryue.vuequests.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record ClientTeamInvitation(UUID teamId, String teamName, GameProfile inviter) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientTeamInvitation> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ClientTeamInvitation::teamId,
            ByteBufCodecs.stringUtf8(64), ClientTeamInvitation::teamName,
            ByteBufCodecs.GAME_PROFILE, ClientTeamInvitation::inviter,
            ClientTeamInvitation::new);
}
