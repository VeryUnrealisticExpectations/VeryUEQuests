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

public record CreateTeamPayload(UUID requestId) implements TeamRequestPayload {
    public static final Type<CreateTeamPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "create_team"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateTeamPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CreateTeamPayload::requestId,
            CreateTeamPayload::new);

    @Override
    public Set<UUID> apply(UserManager manager, ServerPlayer player) { return manager.createTeam(player); }
    @Override
    public Type<CreateTeamPayload> type() { return TYPE; }
}
