package dev.veryue.vuequests.network;

import dev.veryue.vuequests.UserManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;
import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record RenameTeamPayload(UUID requestId, UUID teamId, String name) implements TeamRequestPayload {
    public static final Type<RenameTeamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "rename_team"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RenameTeamPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RenameTeamPayload::requestId,
            UUIDUtil.STREAM_CODEC, RenameTeamPayload::teamId,
            ByteBufCodecs.stringUtf8(256), RenameTeamPayload::name,
            RenameTeamPayload::new);

    @Override
    public Set<UUID> apply(UserManager manager, ServerPlayer player) { return manager.renameTeam(player.getUUID(), teamId, name); }
    @Override
    public Type<RenameTeamPayload> type() { return TYPE; }
}
