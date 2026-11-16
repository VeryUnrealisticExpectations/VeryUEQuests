package dev.veryue.vuequests.gametest;

import com.google.gson.GsonBuilder;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.ClientTeam;
import dev.veryue.vuequests.client.ClientTeamData;
import dev.veryue.vuequests.client.ClientTeamInvitation;
import dev.veryue.vuequests.network.CreateTeamPayload;
import dev.veryue.vuequests.network.TeamSyncPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public class TeamClientGameTests implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        int failures = 0;
        Map<String, String> environments = new LinkedHashMap<>();
        try {
            try (var singleplayer = context.worldBuilder().create()) {
                singleplayer.getConnection().waitForChunksDownload();
                failures += new TeamClientScenarios(context, singleplayer.getServer(), singleplayer.getConnection(), "singleplayer").run();
                seedDisconnectState(context);
            }
            assertDisconnectCleanup(context);
            environments.put("singleplayer", "completed");
        } catch (Throwable exception) {
            environments.put("singleplayer", exception.toString());
            failures++;
        }
        try {
            var properties = new Properties();
            properties.setProperty("server-ip", "127.0.0.1");
            try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                properties.setProperty("server-port", Integer.toString(socket.getLocalPort()));
            }
            properties.setProperty("view-distance", "3");
            properties.setProperty("simulation-distance", "3");
            try (var server = context.worldBuilder().createServer(properties)) {
                try (var connection = server.connect()) {
                    connection.waitForChunksDownload();
                    failures += new TeamClientScenarios(context, server, connection, "dedicated").run();
                    seedDisconnectState(context);
                }
                assertDisconnectCleanup(context);
            }
            environments.put("dedicated", "completed");
        } catch (Throwable exception) {
            environments.put("dedicated", exception.toString());
            failures++;
        }
        try {
            Path directory = Path.of(System.getProperty("vuequests.tests.reportDirectory"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("team-client-summary.json"), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(Map.of("environments", environments, "failures", failures)));
        } catch (Exception exception) { throw new AssertionError(exception); }
        if (failures != 0) throw new AssertionError("Team design verification failed in " + failures + " cases; see team client reports");
    }

    void seedDisconnectState(ClientGameTestContext context) {
        context.runOnClient(client -> {
            UUID team = UUID.randomUUID();
            var profile = client.player.getGameProfile();
            ClientTeamData.update(new TeamSyncPayload(Optional.of(new ClientTeam(team, "Disconnect", profile.id(), List.of(profile), true, true)),
                    List.of(new ClientTeamInvitation(UUID.randomUUID(), "Pending", profile))));
            ClientTeamData.request(CreateTeamPayload::new, payload -> { });
            QuestTestAssertions.check(ClientTeamData.isPending());
            ClientQuestData.setDataStatus(false, Component.literal("Unavailable fixture"));
        });
    }

    void assertDisconnectCleanup(ClientGameTestContext context) {
        context.waitFor(client -> ClientQuestData.getUser() == null && ClientTeamData.getTeam() == null
                && ClientTeamData.getInvitations().isEmpty() && !ClientTeamData.isPending()
                && !ClientQuestData.isDataReady() && ClientQuestData.getDataError().getString().isEmpty());
    }
}
