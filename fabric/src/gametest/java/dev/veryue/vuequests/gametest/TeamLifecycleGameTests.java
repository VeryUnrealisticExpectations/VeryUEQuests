package dev.veryue.vuequests.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.TeamSetting;
import dev.veryue.vuequests.models.objective.ObjectiveSubmitItem;
import dev.veryue.vuequests.network.QuestSyncPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

public class TeamLifecycleGameTests {
    @GameTest(maxTicks = 200)
    public void lifecycleCheckpoint(GameTestHelper helper) throws Exception {
        String phase = System.getProperty("vuequests.tests.lifecycle", "");
        if (phase.equals("prepare-restart")) prepareRestart(helper);
        else if (phase.equals("verify-restart")) verifyRestart(helper);
        else helper.succeed();
    }

    void prepareRestart(GameTestHelper helper) throws Exception {
        JsonObject manifest = new JsonObject();
        try (var s = new TeamTestScenario(helper)) {
            var manager = VeryUEMod.INSTANCE.getUserManager();
            manager.getUser(s.owner.getUUID()).orElseThrow().getQuestObjectiveProgress().put(s.objective.getObjectiveId(),
                    new QuestObjectiveProgress<>(s.objective.getObjectiveId(), s.objective.getType(), 10));
            manager.getUser(s.member.getUUID()).orElseThrow().getQuestObjectiveProgress().put(s.objective.getObjectiveId(),
                    new QuestObjectiveProgress<>(s.objective.getObjectiveId(), s.objective.getType(), 20));
            manager.createTeam(s.owner);
            var team = manager.getPlayerTeam(s.owner.getUUID()).orElseThrow();
            manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name());
            manager.respondToInvitation(s.member, team.getUuid(), true);
            team = manager.getTeam(team.getUuid()).orElseThrow();
            team.getObjectiveProgress(s.objective).setCurrentProgress(60);
            UUID claim = UUID.randomUUID();
            team.getClaimedRewards(s.member.getUUID()).add(claim);
            manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, false);
            manager.saveAllUsers().join();
            manifest.addProperty("owner", s.owner.getUUID().toString());
            manifest.addProperty("member", s.member.getUUID().toString());
            manifest.addProperty("team", team.getUuid().toString());
            manifest.addProperty("objective", s.objective.getObjectiveId().toString());
            manifest.addProperty("claim", claim.toString());
            manifest.addProperty("process", ProcessHandle.current().pid());
            manifest.addProperty("world", s.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
        }
        VeryUEMod.INSTANCE.getUserManager().saveAllUsers().join();
        Path path = Path.of(System.getProperty("vuequests.tests.restartManifest"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        helper.succeed();
    }

    void verifyRestart(GameTestHelper helper) throws Exception {
        Path path = Path.of(System.getProperty("vuequests.tests.restartManifest"));
        JsonObject manifest = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        helper.assertFalse(manifest.get("process").getAsLong() == ProcessHandle.current().pid(), "separate JVM process");
        var server = helper.getLevel().getServer();
        helper.assertValueEqual(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString(), manifest.get("world").getAsString(), "same saved world");
        UUID owner = UUID.fromString(manifest.get("owner").getAsString());
        UUID member = UUID.fromString(manifest.get("member").getAsString());
        UUID teamId = UUID.fromString(manifest.get("team").getAsString());
        UUID claim = UUID.fromString(manifest.get("claim").getAsString());
        var manager = VeryUEMod.INSTANCE.getUserManager();
        var team = manager.getPlayerTeam(owner).orElseThrow();
        helper.assertValueEqual(team.getUuid(), teamId, "startup-restored team");
        helper.assertValueEqual(manager.getPlayerTeam(member).orElseThrow().getUuid(), teamId, "startup-restored member index");
        helper.assertValueEqual(team.getOwnerId(), owner, "startup-restored owner");
        helper.assertTrue(team.isSharingProgressWithTeamMembers() && !team.isKeepProgressWhenLeavingTeam(), "persisted settings");
        helper.assertValueEqual(team.getClaimedRewards(member), Set.of(claim), "persisted member rewards");
        helper.assertTrue(manager.getUser(owner).isEmpty(), "progress gated before login");
        var objective = new ObjectiveSubmitItem(UUID.fromString(manifest.get("objective").getAsString()), new ItemStack(Items.DIAMOND), 100);
        helper.assertValueEqual(team.getObjectiveProgress(objective).getCurrentProgress(), 60, "persisted typed progress");
        try (var s = new TeamTestScenario(helper)) {
            var loggedInOwner = s.login(owner, "owner");
            var loggedInMember = s.login(member, "member");
            helper.assertValueEqual(QuestSyncPayload.create(loggedInOwner).user().getObjectiveProgress(objective).getCurrentProgress(), 60, "initial owner projection");
            helper.assertValueEqual(QuestSyncPayload.create(loggedInMember).user().getClaimedRewards(), Set.of(claim), "initial member reward projection");
            helper.assertTrue(QuestSyncPayload.create(loggedInOwner).user().getClaimedRewards().isEmpty(), "initial projection privacy");
        }
        manifest.addProperty("verifiedProcess", ProcessHandle.current().pid());
        Files.writeString(path.resolveSibling("team-restart-verified.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        helper.succeed();
    }
}
