package dev.veryue.vuequests.client;

import dev.veryue.vuequests.network.TeamActionResultPayload;
import dev.veryue.vuequests.network.TeamRequestPayload;
import dev.veryue.vuequests.network.TeamSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class ClientTeamData {
    private static List<ClientTeamInvitation> invitations = List.of();
    private static ClientTeam team;
    private static UUID pendingRequest;
    private static Component feedback = Component.empty();
    private static boolean failed;
    private static long revision;

    @Nullable
    public static ClientTeam getTeam() {
        return team;
    }

    public static Collection<ClientTeamInvitation> getInvitations() {
        return invitations;
    }

    public static long getRevision() {
        return revision;
    }

    public static boolean isPending() {
        return pendingRequest != null;
    }

    public static Component getFeedback() {
        return feedback;
    }

    public static boolean hasError() {
        return failed;
    }

    public static void refresh() {
        revision++;
    }

    public static void update(TeamSyncPayload snapshot) {
        team = snapshot.team().orElse(null);
        invitations = snapshot.invitations();
        revision++;
    }

    public static void request(Function<UUID, TeamRequestPayload> create, Consumer<CustomPacketPayload> send) {
        if (isPending() || !ClientQuestData.isDataReady()) {
            return;
        }
        pendingRequest = UUID.randomUUID();
        feedback = Component.empty();
        failed = false;
        revision++;
        send.accept(create.apply(pendingRequest));
    }

    public static void complete(TeamActionResultPayload result) {
        if (!result.requestId().equals(pendingRequest)) {
            return;
        }
        pendingRequest = null;
        feedback = result.message();
        failed = !result.success();
        revision++;
    }

    public static void clear() {
        team = null;
        invitations = List.of();
        pendingRequest = null;
        feedback = Component.empty();
        failed = false;
        revision++;
    }
}
