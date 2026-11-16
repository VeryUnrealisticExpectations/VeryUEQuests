package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.models.TeamSetting;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.UUID;

public class TeamInvitationGameTests {
    @GameTest(maxTicks = 200)
    public void invitationsRequireOnlineTeamlessRecipientsAndDeduplicate(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name());
            s.manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name());
            s.equal(s.manager.getInvitations(s.member.getUUID()).size(), 1, "deduplicated invitation");
            s.rejects(() -> s.manager.invite(s.owner.getUUID(), team.getUuid(), "not_online"));
            s.rejects(() -> s.manager.invite(s.owner.getUUID(), team.getUuid(), s.owner.getGameProfile().name()));
            s.manager.respondToInvitation(s.member, team.getUuid(), true);
            s.check(s.manager.getInvitations(s.member.getUUID()).isEmpty(), "accepted invitation removed");
            s.rejects(() -> s.manager.respondToInvitation(s.member, team.getUuid(), true));
            s.rejects(() -> s.manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name()));
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void joiningAndCreationClearOtherInvitations(GameTestHelper helper) {
        for (boolean create : new boolean[]{true, false}) {
            try (var s = new TeamTestScenario(helper)) {
                var team = s.create();
                s.manager.createTeam(s.other);
                var second = s.manager.getPlayerTeam(s.other.getUUID()).orElseThrow();
                s.manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name());
                s.manager.invite(s.other.getUUID(), second.getUuid(), s.member.getGameProfile().name());
                s.equal(s.manager.getInvitations(s.member.getUUID()).size(), 2, "multiple pending invitations");
                if (create) s.manager.createTeam(s.member);
                else s.manager.respondToInvitation(s.member, team.getUuid(), true);
                s.check(s.manager.getInvitations(s.member.getUUID()).isEmpty(), "other invitations cleared");
                s.rejects(() -> s.manager.respondToInvitation(s.member, second.getUuid(), true));
                s.check(!s.manager.getPlayerTeam(s.member.getUUID()).orElseThrow().getUuid().equals(second.getUuid()), "never automatically leaves another team");
            }
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void denialAndRecipientLogoutExpireSessionInvitations(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name());
            s.manager.respondToInvitation(s.member, team.getUuid(), false);
            s.check(s.manager.getInvitations(s.member.getUUID()).isEmpty(), "denied invitation removed");
            s.rejects(() -> s.manager.respondToInvitation(s.member, team.getUuid(), false));
            s.manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name());
            s.logout(s.member);
            var returned = s.login(s.member.getUUID(), "member");
            s.check(s.manager.getInvitations(returned.getUUID()).isEmpty(), "recipient session expired");
            s.rejects(() -> s.manager.respondToInvitation(returned, team.getUuid(), true));
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void ownerLogoutKeepsTeamAndRecipientInvitation(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.manager.invite(s.owner.getUUID(), team.getUuid(), s.member.getGameProfile().name());
            s.logout(s.owner);
            s.check(s.manager.getTeam(team.getUuid()).isPresent(), "owner logout preserves team");
            s.check(s.manager.getPlayerTeam(s.owner.getUUID()).isPresent(), "owner membership retained");
            s.equal(s.manager.getInvitations(s.member.getUUID()).size(), 1, "recipient invitation retained");
            s.manager.respondToInvitation(s.member, team.getUuid(), true);
            s.equal(s.manager.getPlayerTeam(s.member.getUUID()).orElseThrow().getOwnerId(), s.owner.getUUID(), "offline owner remains owner");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void permissionsAndCrossTeamRequestsAreRejected(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.join(s.member, team);
            s.manager.createTeam(s.other);
            var otherTeam = s.manager.getPlayerTeam(s.other.getUUID()).orElseThrow();
            int writes = s.writes.size();
            s.rejects(() -> s.manager.renameTeam(s.member.getUUID(), team.getUuid(), "forged"));
            s.rejects(() -> s.manager.changeSetting(s.member.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, false));
            s.rejects(() -> s.manager.invite(s.member.getUUID(), team.getUuid(), s.other.getGameProfile().name()));
            s.rejects(() -> s.manager.kickMember(s.member.getUUID(), team.getUuid(), s.owner.getUUID()));
            s.rejects(() -> s.manager.kickMember(s.owner.getUUID(), team.getUuid(), s.owner.getUUID()));
            s.rejects(() -> s.manager.kickMember(s.owner.getUUID(), team.getUuid(), UUID.randomUUID()));
            s.rejects(() -> s.manager.leaveTeam(s.member.getUUID(), otherTeam.getUuid()));
            s.rejects(() -> s.manager.renameTeam(s.owner.getUUID(), otherTeam.getUuid(), "forged"));
            s.rejects(() -> s.manager.createTeam(s.member));
            s.equal(s.writes.size(), writes, "rejections do not persist changes");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void duplicateNamesAreAllowedButNamesAreValidated(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            var team = s.create();
            s.manager.createTeam(s.other);
            var otherTeam = s.manager.getPlayerTeam(s.other.getUUID()).orElseThrow();
            s.manager.renameTeam(s.owner.getUUID(), team.getUuid(), "  Same Name  ");
            s.manager.renameTeam(s.other.getUUID(), otherTeam.getUuid(), "Same Name");
            s.equal(s.team().getName(), "Same Name", "trimmed name");
            s.equal(s.manager.getTeam(otherTeam.getUuid()).orElseThrow().getName(), "Same Name", "duplicate names");
            s.rejects(() -> s.manager.renameTeam(s.owner.getUUID(), team.getUuid(), "  "));
            s.rejects(() -> s.manager.renameTeam(s.owner.getUUID(), team.getUuid(), "x".repeat(65)));
            s.manager.renameTeam(s.owner.getUUID(), team.getUuid(), "x".repeat(64));
            s.equal(s.team().getName().length(), 64, "name length boundary");
        }
        if (helper != null) helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void repeatedSettingDoesNotReseedSharedProgress(GameTestHelper helper) {
        try (var s = new TeamTestScenario(helper)) {
            s.seed(s.owner, 10);
            var team = s.create();
            s.seed(s.owner, 60);
            int writes = s.writes.size();
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.SHARING_PROGRESS, true);
            s.equal(s.progress(s.team()), 60, "sharing no-op preserves progress");
            s.equal(s.writes.size(), writes, "sharing no-op performs no writes");
            s.manager.changeSetting(s.owner.getUUID(), team.getUuid(), TeamSetting.KEEP_PROGRESS, false);
            s.check(s.team().isSharingProgressWithTeamMembers(), "single-setting request preserves other flag");
        }
        if (helper != null) helper.succeed();
    }
}
