package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.UserManager;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.ClientTeamData;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import dev.veryue.vuequests.network.TeamRequestPayload;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class TeamClientScenario implements AutoCloseable {
    final ClientGameTestContext context;
    final TestServerContext server;
    final TestServerConnection connection;
    final TeamTestScenario state;
    final UserManager originalManager;
    final QuestData originalData;

    TeamClientScenario(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        this.context = context;
        this.server = server;
        this.connection = connection;
        originalManager = server.computeOnServer(instance -> VeryUEMod.INSTANCE.getUserManager());
        originalData = server.computeOnServer(instance -> QuestReloadDelegate.INSTANCE.getData());
        state = server.computeOnServer(instance -> {
            TeamTestEnvironment.level.set(connection.getServerPlayer().level());
            try {
                var fixture = new TeamTestScenario(null);
                replaceManager(fixture.manager);
                fixture.manager.loadPlayer(connection.getServerPlayer());
                var quests = new HashMap<>(originalData.quests());
                var sections = new HashMap<>(originalData.sections());
                quests.put(fixture.quest.questId(), fixture.quest);
                sections.put(fixture.quest.sectionUUID(), new Section(fixture.quest.sectionUUID(), "Team Test", Items.BOOK, List.of(fixture.quest)));
                QuestReloadDelegate.INSTANCE.reload(new QuestTestLoader(new QuestData(sections, quests)), instance.getResourceManager(), instance.registryAccess());
                VeryUEMod.INSTANCE.restoreProgressRecipientAttributes(connection.getServerPlayer());
                return fixture;
            } finally { TeamTestEnvironment.level.remove(); }
        });
        context.setScreen(() -> null);
        refresh();
        context.waitFor(client -> ClientQuestData.isDataReady() && ClientTeamData.getTeam() == null);
    }

    static void replaceManager(UserManager manager) {
        try {
            Field field = VeryUEMod.class.getDeclaredField("userManager");
            field.setAccessible(true);
            field.set(VeryUEMod.INSTANCE, manager);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    void onPlayer(Consumer<ServerPlayer> action) { server.runOnServer(instance -> action.accept(connection.getServerPlayer())); }
    <T> T fromPlayer(Function<ServerPlayer, T> action) { return server.computeOnServer(instance -> action.apply(connection.getServerPlayer())); }
    UUID playerId() { return fromPlayer(ServerPlayer::getUUID); }
    User active(ServerPlayer player) { return state.manager.getUser(player.getUUID()).orElseThrow(); }

    void request(Function<UUID, TeamRequestPayload> create, boolean success) {
        context.runOnClient(client -> ClientTeamData.request(create, ClientPlayNetworking::send));
        connection.waitForServerboundPackets();
        connection.waitForClientboundPackets();
        context.waitFor(client -> !ClientTeamData.isPending());
        context.runOnClient(client -> QuestTestAssertions.equal(ClientTeamData.hasError(), !success));
    }

    void send(CustomPacketPayload payload) {
        context.runOnClient(client -> ClientPlayNetworking.send(payload));
        connection.waitForServerboundPackets();
        connection.waitForClientboundPackets();
    }

    void refresh() { send(QuestScreenOpenedPayload.INSTANCE); }

    @Override
    public void close() {
        context.setScreen(() -> null);
        server.runOnServer(instance -> {
            var fakePlayers = state.online.stream().map(ServerPlayer::getUUID).toList();
            try { state.close(); }
            finally {
                replaceManager(originalManager);
                fakePlayers.forEach(originalManager::unloadPlayer);
                QuestReloadDelegate.INSTANCE.reload(new QuestTestLoader(originalData), instance.getResourceManager(), instance.registryAccess());
                VeryUEMod.INSTANCE.restoreProgressRecipientAttributes(connection.getServerPlayer());
            }
        });
        context.runOnClient(client -> ClientTeamData.clear());
        refresh();
    }
}
