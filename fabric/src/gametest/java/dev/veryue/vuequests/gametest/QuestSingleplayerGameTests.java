package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.client.ClientQuestData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class QuestSingleplayerGameTests implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksDownload();
            new QuestClientScenarios(context, singleplayer.getServer(), singleplayer.getConnection()).run("singleplayer");
        }
        context.waitFor(client -> ClientQuestData.getUser() == null && ClientQuestData.getData().quests().isEmpty());
    }
}
