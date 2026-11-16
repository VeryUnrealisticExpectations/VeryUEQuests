package dev.veryue.vuequests.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.UserManager;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.TeamSetting;
import dev.veryue.vuequests.models.TeamUser;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TeamPersistenceGameTests {
    @GameTest(maxTicks = 200)
    public void restartRestoresMembershipProgressAndIndividualClaims(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.join(s.member, team);
            s.seed(s.owner, 60);
            UUID claim = UUID.randomUUID();
            s.team().getClaimedRewards(s.member.getUUID()).add(claim);
            s.logout(s.member);
            s.manager.invite(s.owner.getUUID(), team.getUuid(), s.other.getGameProfile().name());
            s.manager.stop();
            s.manager = new UserManager(s.server, s.players, s.teams);
            s.check(s.manager.getPlayerTeam(s.owner.getUUID()).isPresent(), "membership restored before login");
            s.check(s.manager.getUser(s.owner.getUUID()).isEmpty(), "personal readiness required despite sharing");
            s.manager.loadPlayer(s.owner);
            s.equal(s.progress(s.owner), 60, "shared progress restored");
            s.equal(s.team().getClaimedRewards(s.member.getUUID()), Set.of(claim), "offline active claims restored");
            s.check(s.manager.getInvitations(s.other.getUUID()).isEmpty(), "invitations not persisted");
            var returned = s.login(s.member.getUUID(), "member");
            s.equal(s.progress(returned), 60, "member active snapshot restored");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void changedMemberNameIsRefreshedAndPersistedOnLogin(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.join(s.member, team);
            s.logout(s.member);
            var returned = s.login(s.member.getUUID(), "renamed");
            s.equal(s.team().getMembers().get(returned.getUUID()).lastKnownName(), returned.getGameProfile().name(), "last-known name refresh");
            s.restart();
            s.equal(s.team().getMembers().get(returned.getUUID()).lastKnownName(), returned.getGameProfile().name(), "refreshed name persisted");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void missingKnownPersonalFileDisablesQuestAccess(GameTestHelper helper) throws Exception {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.join(s.member, team);
            s.manager.unloadPlayer(s.member.getUUID());
            s.flush();
            Files.delete(s.players.userPath(s.member.getUUID()));
            s.manager.loadPlayer(s.member);
            s.check(!s.manager.isReady(s.member.getUUID()), "missing restore point is unavailable");
            s.check(s.manager.getUser(s.member.getUUID()).isEmpty(), "shared team does not bypass readiness");
            s.rejects(() -> s.manager.leaveTeam(s.member.getUUID(), team.getUuid()));
            s.check(!Files.exists(s.players.userPath(s.member.getUUID())), "no fabricated personal record");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void unreadablePersonalDataRequiresNextLoginAfterRepair(GameTestHelper helper) throws Exception {
        try (var s = new TeamTestScenario(helper)) {
            s.seed(s.member, 20);
            s.manager.unloadPlayer(s.member.getUUID());
            s.flush();
            String original = Files.readString(s.players.userPath(s.member.getUUID()));
            Files.writeString(s.players.userPath(s.member.getUUID()), "{");
            s.manager.loadPlayer(s.member);
            s.check(!s.manager.isReady(s.member.getUUID()), "corrupt personal data unavailable");
            s.equal(Files.readString(s.players.userPath(s.member.getUUID())), "{", "corrupt source untouched");
            Files.writeString(s.players.userPath(s.member.getUUID()), original);
            s.manager.loadPlayer(s.member);
            s.check(!s.manager.isReady(s.member.getUUID()), "no same-session retry");
            s.manager.unloadPlayer(s.member.getUUID());
            s.manager.loadPlayer(s.member);
            s.check(s.manager.isReady(s.member.getUUID()), "reconnect recovers access");
            s.equal(s.progress(s.member), 20, "repaired progress preserved");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void unavailableOfflineRecordsRejectWholeTransitionsBeforeWrites(GameTestHelper helper) throws Exception {
        for (boolean missing : new boolean[]{true, false}) for (int operation = 0; operation < 3; operation++) {
            try (var s = new TeamTestScenario(helper)) {
                var team = s.create();
                s.join(s.member, team);
                s.logout(s.member);
                s.flush();
                if (missing) Files.delete(s.players.userPath(s.member.getUUID()));
                else Files.writeString(s.players.userPath(s.member.getUUID()), "{");
                String originalTeam = Files.readString(s.teams.userPath(team.getUuid()));
                int writes = s.writes.size();
                int selected = operation;
                s.rejects(() -> {
                    if (selected == 0) s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
                    else if (selected == 1) s.manager.kickMember(s.owner.getUUID(), team.getUuid(), s.member.getUUID());
                    else s.manager.leaveTeam(s.owner.getUUID(), team.getUuid());
                });
                s.equal(s.writes.size(), writes, "read failure performs no writes");
                s.equal(Files.readString(s.teams.userPath(team.getUuid())), originalTeam, "team file untouched");
                s.check(s.team().containsPlayer(s.member.getUUID()) && s.team().isSharingProgressWithTeamMembers(), "live team unchanged");
                s.check(!s.manager.hasFatalFailure(), "personal read failure is nonfatal");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void invalidTeamFieldsAndUuidStopRestorationWithoutRewriting(GameTestHelper helper) throws Exception {
        for (String field : List.of("uuid", "name", "members", "sharingProgressWithTeamMembers", "keepProgressWhenLeavingTeam")) {
            try (var s = new TeamTestScenario(helper)) {
                var team = s.create();
                s.manager.stop();
                JsonObject json = JsonParser.parseString(Files.readString(s.teams.userPath(team.getUuid()))).getAsJsonObject();
                if (field.equals("uuid")) json.addProperty("uuid", UUID.randomUUID().toString());
                else json.remove(field);
                String invalid = json.toString();
                Files.writeString(s.teams.userPath(team.getUuid()), invalid);
                boolean rejected = false;
                try { new UserManager(s.server, s.players, s.teams); }
                catch (IllegalStateException exception) { rejected = true; }
                s.check(rejected, "invalid team rejected: " + field);
                s.equal(Files.readString(s.teams.userPath(team.getUuid())), invalid, "invalid team left untouched");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void duplicateMembershipStopsRestorationWithoutRewriting(GameTestHelper helper) throws Exception {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.manager.stop();
            var duplicate = new TeamUser(UUID.randomUUID(), "Duplicate", s.owner.getUUID(), s.owner.getGameProfile().name());
            s.teams.saveUser(duplicate);
            String original = Files.readString(s.teams.userPath(team.getUuid()));
            String second = Files.readString(s.teams.userPath(duplicate.getUuid()));
            boolean rejected = false;
            try { new UserManager(s.server, s.players, s.teams); }
            catch (IllegalStateException exception) { rejected = true; }
            s.check(rejected, "duplicate membership rejected");
            s.equal(Files.readString(s.teams.userPath(team.getUuid())), original, "first source unchanged");
            s.equal(Files.readString(s.teams.userPath(duplicate.getUuid())), second, "second source unchanged");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void teamCodecHasOnlyMemberClaimsAndStrictNestedMetadata(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            var ops = s.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonObject json = TeamUser.CODEC.encodeStart(ops, team).getOrThrow().getAsJsonObject();
            s.check(!json.has("claimed_rewards"), "no unused team-level ledger");
            for (String field : List.of("owner", "members")) {
                JsonObject invalid = json.deepCopy();
                invalid.getAsJsonObject("members").remove(field);
                boolean rejected = false;
                try { rejected = TeamUser.CODEC.parse(ops, invalid).error().isPresent(); }
                catch (RuntimeException exception) { rejected = true; }
                s.check(rejected, "required membership field " + field);
            }
            for (String field : List.of("lastKnownName", "claimedRewards")) {
                JsonObject invalid = json.deepCopy();
                invalid.getAsJsonObject("members").getAsJsonObject("members").getAsJsonObject(s.owner.getUUID().toString()).remove(field);
                boolean rejected = false;
                try { rejected = TeamUser.CODEC.parse(ops, invalid).error().isPresent(); }
                catch (RuntimeException exception) { rejected = true; }
                s.check(rejected, "required member field " + field);
            }
            JsonObject invalid = json.deepCopy();
            invalid.getAsJsonObject("members").addProperty("owner", s.other.getUUID().toString());
            boolean rejected = false;
            try { rejected = TeamUser.CODEC.parse(ops, invalid).error().isPresent(); }
            catch (RuntimeException exception) { rejected = true; }
            s.check(rejected, "owner must be a member");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void personalCodecRetainsLegacyClaimedRewards(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            UUID claim = UUID.randomUUID();
            var user = new PlayerUser(s.member.getUUID(), Set.of(s.quest.questId()), Set.of(s.objective.getObjectiveId()), Set.of(claim));
            var ops = s.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonObject json = PlayerUser.CODEC.encodeStart(ops, user).getOrThrow().getAsJsonObject();
            json.remove("quest_objective_progress");
            PlayerUser decoded = PlayerUser.CODEC.parse(ops, json).getOrThrow();
            s.equal(decoded.getClaimedRewards(), Set.of(claim), "legacy claims remain readable");
            s.equal(decoded.getCompletedQuests(), user.getCompletedQuests(), "legacy completions readable");
            s.check(decoded.getQuestObjectiveProgress().isEmpty(), "legacy typed-progress default");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void asynchronousSnapshotsDetachMutableProgressAndSerializeWrites(GameTestHelper helper) throws Exception {
        try (var s = new TeamTestScenario(helper)) {
            s.seed(s.owner, 10);
            var team = s.create();
            s.seed(s.owner, 60);
            s.players.entered = new CountDownLatch(1);
            s.players.release = new CountDownLatch(1);
            var pending = s.manager.saveAllUsers();
            try {
                s.check(s.players.entered.await(5, TimeUnit.SECONDS), "background writer entered");
                s.personalCache().get(s.owner.getUUID()).getObjectiveProgress(s.objective).setCurrentProgress(40);
                s.active(s.owner).getObjectiveProgress(s.objective).setCurrentProgress(80);
                s.check(!pending.isDone(), "ordinary save is asynchronous");
            } finally { s.players.release.countDown(); }
            pending.join();
            s.equal(s.progress(s.saved(s.owner)), 10, "detached personal snapshot");
            s.equal(s.progress(s.teams.loadUser(team.getUuid())), 60, "detached team snapshot");
            s.check(s.players.lastWriteThread != Thread.currentThread(), "background file writes");
            s.check(s.players.lastWriteThread == s.teams.lastWriteThread, "single ordered writer");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void orderedTransitionWritesAndDeletionCannotBeOvertakenByAutosave(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.join(s.member, team);
            s.manager.saveAllUsers();
            s.writes.clear();
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
            List<UUID> ordered = new ArrayList<>(List.of(s.owner.getUUID(), s.member.getUUID()));
            ordered.sort(UUID::compareTo);
            List<String> tail = s.writes.subList(s.writes.size() - 3, s.writes.size());
            s.equal(new ArrayList<>(tail), List.of("save player " + ordered.get(0), "save player " + ordered.get(1), "save team " + team.getUuid()), "ordered transition writes");
            s.manager.saveAllUsers();
            s.manager.leaveTeam(s.owner.getUUID(), team.getUuid());
            s.flush();
            s.check(!Files.exists(s.teams.userPath(team.getUuid())), "no resurrection after queued autosave");
            int deletion = s.writes.lastIndexOf("delete team " + team.getUuid());
            s.check(deletion > s.writes.lastIndexOf("save team " + team.getUuid()), "delete follows every team write");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void transitionWriteFailureLatchesAndBlocksStaleFlushes(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.teams.failedSaves.add(team.getUuid());
            s.fatal(() -> s.join(s.member, team));
            s.check(!s.manager.getTeam(team.getUuid()).orElseThrow().containsPlayer(s.member.getUUID()), "failed transition not committed");
            int writes = s.writes.size();
            s.fatal(() -> s.manager.saveAllUsers().join());
            s.fatal(s.manager::stop);
            s.equal(s.writes.size(), writes, "no autosave or shutdown rewrite after partial failure");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void failedAutosaveLogoutShutdownAndDeletionAreFatal(GameTestHelper helper) {
        for (int operation = 0; operation < 4; operation++) {
            try (var s = new TeamTestScenario(helper)) {
                var team = s.create();
                int selected = operation;
                if (selected == 3) s.teams.failedDeletes.add(team.getUuid());
                else s.players.failedSaves.add(s.owner.getUUID());
                s.fatal(() -> {
                    if (selected == 0) s.manager.saveAllUsers().join();
                    else if (selected == 1) { s.manager.unloadPlayer(s.owner.getUUID()); s.manager.saveAllUsers().join(); }
                    else if (selected == 2) s.manager.stop();
                    else s.manager.leaveTeam(s.owner.getUUID(), team.getUuid());
                });
                s.check(!s.manager.isReady(s.owner.getUUID()), "fatal failure disables quest access");
                int writes = s.writes.size();
                s.fatal(() -> s.manager.renameTeam(s.owner.getUUID(), team.getUuid(), "late"));
                s.equal(s.writes.size(), writes, "no writes after fatal latch");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void requiredSnapshotEncodingFailureIsFatalBeforeWrites(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            s.seed(s.owner, 10);
            var value = s.active(s.owner).getObjectiveProgress(s.objective);
            s.active(s.owner).getQuestObjectiveProgress().put(UUID.randomUUID(), value);
            int writes = s.writes.size();
            s.fatal(() -> s.manager.saveAllUsers().join());
            s.equal(s.writes.size(), writes, "failed snapshot queues no file writes");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void reconnectWaitsForQueuedLogoutSnapshot(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            s.seed(s.member, 35);
            s.manager.unloadPlayer(s.member.getUUID());
            s.manager.loadPlayer(s.member);
            s.equal(s.progress(s.member), 35, "immediate reconnect sees logout save");
        }
        if (helper != null) helper.succeed();
    }
}
