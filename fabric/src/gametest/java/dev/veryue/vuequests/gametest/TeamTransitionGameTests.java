package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.models.TeamSetting;
import dev.veryue.vuequests.models.TeamUser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.Set;
import java.nio.file.Files;
import java.util.UUID;

public class TeamTransitionGameTests {
    @GameTest(maxTicks = 200)
    public void createSeedsOwnerAndPreservesPersonal(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            s.seed(s.owner, 10);
            UUID claim = UUID.randomUUID();
            s.active(s.owner).getClaimedRewards().add(claim);
            TeamUser team = s.create();
            s.equal(team.getOwnerId(), s.owner.getUUID(), "owner identity");
            s.equal(team.getMembers().keySet(), Set.of(s.owner.getUUID()), "first member");
            s.check(team.isSharingProgressWithTeamMembers() && team.isKeepProgressWhenLeavingTeam(), "creation defaults");
            s.equal(s.progress(s.owner), 10, "seeded shared progress");
            s.equal(s.progress(s.saved(s.owner)), 10, "preserved personal progress");
            s.equal(team.getClaimedRewards(s.owner.getUUID()), Set.of(claim), "owner claims");
            s.equal(team.getName(), s.owner.getGameProfile().name() + "'s Team", "default name");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void joiningNeverImportsNewcomerProgress(GameTestHelper helper) {
        for (boolean sharing : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                s.seed(s.owner, 10);
                s.seed(s.member, 80);
                TeamUser team = s.create();
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, sharing);
                s.join(s.member, team);
                s.equal(s.progress(s.team()), 10, "stored team snapshot");
                s.equal(s.progress(s.member), sharing ? 10 : 80, "joining active progress");
                s.equal(s.progress(s.saved(s.member)), 80, "newcomer restore point");
                s.equal(s.team().getMembers().size(), 2, "membership");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void leaveUsesBothSharingAndCurrentKeepValues(GameTestHelper helper) {
        for (boolean sharing : new boolean[]{true, false}) for (boolean keep : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                s.seed(s.owner, 10);
                s.seed(s.member, 20);
                TeamUser team = s.create();
                s.join(s.member, team);
                s.seed(s.owner, 60);
                UUID ownClaim = UUID.randomUUID();
                UUID foreignClaim = UUID.randomUUID();
                s.team().getClaimedRewards(s.member.getUUID()).add(ownClaim);
                s.team().getClaimedRewards(s.owner.getUUID()).add(foreignClaim);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, sharing);
                if (!sharing) s.seed(s.member, 80);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, keep);
                s.manager.leaveTeam(s.member.getUUID(), team.getUuid());
                s.equal(s.progress(s.member), sharing ? keep ? 60 : 20 : 80, "departure policy");
                s.equal(s.active(s.member).getClaimedRewards(), Set.of(ownClaim), "individual claims survive departure");
                s.check(s.manager.getPlayerTeam(s.member.getUUID()).isEmpty(), "membership removed");
                s.check(!s.team().containsPlayer(s.member.getUUID()), "member map removed");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void disablingDistributesToOfflineMembersRegardlessOfKeep(GameTestHelper helper) {
        for (boolean keep : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                s.seed(s.owner, 10);
                s.seed(s.member, 20);
                TeamUser team = s.create();
                s.join(s.member, team);
                s.seed(s.owner, 60);
                UUID claim = UUID.randomUUID();
                s.team().getClaimedRewards(s.member.getUUID()).add(claim);
                s.logout(s.member);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, keep);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
                s.equal(s.progress(s.owner), 60, "owner distribution");
                s.equal(s.progress(s.saved(s.member)), 60, "offline distribution");
                s.equal(s.saved(s.member).getClaimedRewards(), Set.of(claim), "offline claim transfer");
                s.equal(s.progress(s.team()), 60, "inactive team retained");
                s.check(s.manager.getPlayerTeam(s.member.getUUID()).isPresent(), "offline membership retained");
                s.seed(s.owner, 70);
                s.equal(s.progress(s.team()), 60, "personal updates do not change team snapshot");
                var returned = s.login(s.member.getUUID(), "member");
                s.equal(s.progress(returned), 60, "distributed state on login");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void reenableUsesOnlyOwnerAndRefreshesRestorePoints(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            s.seed(s.owner, 10);
            s.seed(s.member, 20);
            TeamUser team = s.create();
            s.join(s.member, team);
            s.seed(s.owner, 60);
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
            s.equal(s.progress(s.owner), 60, "ten to sixty owner");
            s.equal(s.progress(s.member), 60, "ten to sixty member");
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, true);
            s.equal(s.progress(s.team()), 60, "immediate reenable");
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
            s.seed(s.owner, 70);
            s.seed(s.member, 80);
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, true);
            s.equal(s.progress(s.team()), 70, "owner-only baseline");
            s.equal(s.progress(s.saved(s.member)), 80, "current-period restore point");
            s.seed(s.owner, 90);
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, false);
            s.manager.leaveTeam(s.member.getUUID(), team.getUuid());
            s.equal(s.progress(s.member), 80, "resumed current-period personal progress");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void replacementDiscardsDestinationOnlyCompletions(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            UUID personalOnly = UUID.randomUUID();
            UUID sharedOnly = UUID.randomUUID();
            s.active(s.member).getCompletedQuests().add(personalOnly);
            s.active(s.member).getCompletedObjectives().add(personalOnly);
            TeamUser team = s.create();
            s.join(s.member, team);
            s.team().getCompletedQuests().add(sharedOnly);
            s.team().getCompletedObjectives().add(sharedOnly);
            s.manager.leaveTeam(s.member.getUUID(), team.getUuid());
            s.equal(s.active(s.member).getCompletedQuests(), Set.of(sharedOnly), "replacement rather than merge");
            s.equal(s.active(s.member).getCompletedObjectives(), Set.of(sharedOnly), "objective completion replacement");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void kickUsesCurrentLeavePolicyIncludingOfflineMembers(GameTestHelper helper) {
        for (boolean sharing : new boolean[]{true, false}) for (boolean keep : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                s.seed(s.member, 20);
                TeamUser team = s.create();
                s.join(s.member, team);
                s.seed(s.owner, 60);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, sharing);
                if (!sharing) s.seed(s.member, 80);
                s.logout(s.member);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, keep);
                s.manager.kickMember(s.owner.getUUID(), team.getUuid(), s.member.getUUID());
                s.equal(s.progress(s.saved(s.member)), sharing ? keep ? 60 : 20 : 80, "offline kick policy");
                s.check(s.manager.getPlayerTeam(s.member.getUUID()).isEmpty(), "offline kick membership");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void ownerLeaveDisbandsUsingEveryMembersCurrentPolicy(GameTestHelper helper) {
        for (boolean sharing : new boolean[]{true, false}) for (boolean keep : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                s.seed(s.owner, 10);
                s.seed(s.member, 20);
                TeamUser team = s.create();
                s.join(s.member, team);
                s.seed(s.owner, 60);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, sharing);
                if (!sharing) { s.seed(s.owner, 70); s.seed(s.member, 80); }
                s.manager.invite(s.owner.getUUID(), team.getUuid(), s.other.getGameProfile().name());
                s.logout(s.member);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, keep);
                s.manager.leaveTeam(s.owner.getUUID(), team.getUuid());
                s.equal(s.progress(s.owner), sharing ? keep ? 60 : 10 : 70, "disband owner policy");
                s.equal(s.progress(s.saved(s.member)), sharing ? keep ? 60 : 20 : 80, "disband offline member policy");
                s.check(s.manager.getTeam(team.getUuid()).isEmpty(), "team deleted");
                s.check(!Files.exists(s.teams.userPath(team.getUuid())), "team file deleted");
                s.check(s.manager.getInvitations(s.other.getUUID()).isEmpty(), "disband invalidates invitations");
                s.check(s.manager.getPlayerTeam(s.member.getUUID()).isEmpty(), "no successor owner");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void distributedObjectiveValuesAreIndependent(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            TeamUser team = s.create();
            s.join(s.member, team);
            s.seed(s.owner, 60);
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
            s.active(s.member).getObjectiveProgress(s.objective).setCurrentProgress(80);
            s.equal(s.progress(s.owner), 60, "independent owner objective value");
            s.equal(s.progress(s.team()), 60, "independent inactive team value");
            s.equal(s.progress(s.member), 80, "member owns distributed value");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void rewardClaimsSurviveSwitchesWithoutCopyingOthers(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            UUID ownerClaim = UUID.randomUUID();
            UUID memberClaim = UUID.randomUUID();
            s.active(s.owner).getClaimedRewards().add(ownerClaim);
            s.active(s.member).getClaimedRewards().add(memberClaim);
            int experience = s.member.totalExperience;
            TeamUser team = s.create();
            s.join(s.member, team);
            s.equal(s.team().getClaimedRewards(s.member.getUUID()), Set.of(memberClaim), "joining individual ledger");
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
            s.equal(s.active(s.member).getClaimedRewards(), Set.of(memberClaim), "distributed individual ledger");
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, true);
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, false);
            s.manager.leaveTeam(s.member.getUUID(), team.getUuid());
            s.equal(s.active(s.member).getClaimedRewards(), Set.of(memberClaim), "keep false retains claims");
            s.join(s.member, team);
            s.manager.leaveTeam(s.owner.getUUID(), team.getUuid());
            s.equal(s.active(s.member).getClaimedRewards(), Set.of(memberClaim), "disband retains claims");
            s.manager.createTeam(s.member);
            s.equal(s.active(s.member).getClaimedRewards(s.member.getUUID()), Set.of(memberClaim), "recreation retains claims");
            s.equal(s.member.totalExperience, experience, "transitions do not grant rewards");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void adminResetDoesNotResurrectInactiveClaims(GameTestHelper helper) {
        for (boolean sharing : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                s.active(s.owner).getClaimedRewards().add(s.reward.getRewardId());
                s.active(s.member).getClaimedRewards().add(s.reward.getRewardId());
                TeamUser team = s.create();
                s.join(s.member, team);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, sharing);
                s.active(s.member).resetQuestProgress(s.quest);
                s.check(!s.active(s.member).getClaimedRewards(s.member.getUUID()).contains(s.reward.getRewardId()), "active reset");
                if (sharing) s.check(s.team().getClaimedRewards(s.owner.getUUID()).isEmpty(), "shared reset scope");
                else s.check(s.active(s.owner).getClaimedRewards().contains(s.reward.getRewardId()), "personal reset scope");
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, !sharing);
                s.manager.leaveTeam(s.member.getUUID(), team.getUuid());
                s.check(s.active(s.member).getClaimedRewards().isEmpty(), "stale inactive claims stay cleared");
            }
        }
        if (helper != null) helper.succeed();
    }
    @GameTest(maxTicks = 200)
    public void disabledDepartureIgnoresStaleInactiveClaims(GameTestHelper helper) {
        for (boolean keep : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                var team = s.create();
                s.active(s.member).getClaimedRewards().add(s.reward.getRewardId());
                s.join(s.member, team);
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false);
                s.active(s.member).resetQuestProgress(s.quest);
                s.check(s.team().getClaimedRewards(s.member.getUUID()).contains(s.reward.getRewardId()), "inactive ledger is stale");
                s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, keep);
                s.manager.leaveTeam(s.member.getUUID(), team.getUuid());
                s.check(s.active(s.member).getClaimedRewards().isEmpty(), "disabled departure ignores inactive claims");
            }
        }
        if (helper != null) helper.succeed();
    }

}
