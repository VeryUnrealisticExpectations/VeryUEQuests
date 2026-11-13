package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.client.ClientQuestData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Properties;

public class QuestDedicatedServerGameTests implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        var properties = new Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            properties.setProperty("server-port", Integer.toString(socket.getLocalPort()));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        properties.setProperty("view-distance", "3");
        properties.setProperty("simulation-distance", "3");
        try (var server = context.worldBuilder().createServer(properties); var connection = server.connect()) {
            connection.waitForChunksDownload();
            new QuestClientScenarios(context, server, connection).run("dedicated");
        }
        context.waitFor(client -> ClientQuestData.getUser() == null && ClientQuestData.getData().quests().isEmpty());
    }
}
