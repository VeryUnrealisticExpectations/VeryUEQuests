package dev.veryue.vuequests.network;

import dev.veryue.vuequests.UserManager;
import dev.veryue.vuequests.models.TeamSetting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;
import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record ChangeTeamSettingPayload(UUID requestId, UUID teamId, TeamSetting setting, boolean value) implements TeamRequestPayload {
    public static final Type<ChangeTeamSettingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "change_team_setting"));
    private static final StreamCodec<RegistryFriendlyByteBuf, TeamSetting> SETTING_CODEC = StreamCodec.of(
            (buffer, setting) -> buffer.writeEnum(setting), buffer -> buffer.readEnum(TeamSetting.class));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChangeTeamSettingPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ChangeTeamSettingPayload::requestId,
            UUIDUtil.STREAM_CODEC, ChangeTeamSettingPayload::teamId,
            SETTING_CODEC, ChangeTeamSettingPayload::setting,
            ByteBufCodecs.BOOL, ChangeTeamSettingPayload::value,
            ChangeTeamSettingPayload::new);

    @Override
    public Set<UUID> apply(UserManager manager, ServerPlayer player) { return manager.changeSetting(player.getUUID(), teamId, setting, value); }
    @Override
    public Type<ChangeTeamSettingPayload> type() { return TYPE; }
}
