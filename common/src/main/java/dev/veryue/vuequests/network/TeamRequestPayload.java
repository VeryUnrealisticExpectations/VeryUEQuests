package dev.veryue.vuequests.network;

import dev.veryue.vuequests.UserManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;

public interface TeamRequestPayload extends CustomPacketPayload {
    UUID requestId();
    Set<UUID> apply(UserManager manager, ServerPlayer player);
}
