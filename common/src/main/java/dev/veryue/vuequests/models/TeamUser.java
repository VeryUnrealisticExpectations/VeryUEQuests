package dev.veryue.vuequests.models;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamUser extends User {

    public static final Codec<TeamUser> CODEC = RecordCodecBuilder.create(instance ->
            User.progressCodec(instance)
                    .and(Codec.STRING.fieldOf("name").forGetter(TeamUser::getName))
                    .and(TeamMembership.CODEC.fieldOf("members").forGetter(TeamUser::getMembership))
                    .and(Codec.BOOL.fieldOf("sharingProgressWithTeamMembers").forGetter(TeamUser::isSharingProgressWithTeamMembers))
                    .and(Codec.BOOL.fieldOf("keepProgressWhenLeavingTeam").forGetter(TeamUser::isKeepProgressWhenLeavingTeam))
                    .apply(instance, TeamUser::new));

    private String name;
    private final TeamMembership membership;
    private boolean sharingProgressWithTeamMembers;
    private boolean keepProgressWhenLeavingTeam;

    public TeamUser(UUID uuid, String name, UUID owner, String ownerName) {
        this(uuid, Set.of(), Set.of(), Map.of(), name,
                new TeamMembership(owner, Map.of(owner, new TeamMember(ownerName, Set.of()))), true, true);
    }

    public TeamUser(UUID uuid, Set<UUID> completedQuests, Set<UUID> completedObjectives,
                    Map<UUID, QuestObjectiveProgress<?, ?>> progress, String name, TeamMembership membership,
                    boolean sharingProgressWithTeamMembers, boolean keepProgressWhenLeavingTeam) {
        super(uuid, completedQuests, completedObjectives, Set.of(), progress);
        setName(name);
        this.membership = membership;
        this.sharingProgressWithTeamMembers = sharingProgressWithTeamMembers;
        this.keepProgressWhenLeavingTeam = keepProgressWhenLeavingTeam;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank() || name.length() > 64 || !name.equals(name.strip())) {
            throw new IllegalArgumentException("Team names must be trimmed, nonblank and at most 64 characters");
        }
        this.name = name;
    }

    public TeamMembership getMembership() {
        return membership;
    }

    public UUID getOwnerId() {
        return membership.owner();
    }

    public boolean isSharingProgressWithTeamMembers() {
        return sharingProgressWithTeamMembers;
    }

    public boolean isKeepProgressWhenLeavingTeam() {
        return keepProgressWhenLeavingTeam;
    }

    public void setKeepProgressWhenLeavingTeam(boolean keepProgressWhenLeavingTeam) {
        this.keepProgressWhenLeavingTeam = keepProgressWhenLeavingTeam;
    }

    public void setSharingProgressWithTeamMembers(boolean sharingProgressWithTeamMembers) {
        this.sharingProgressWithTeamMembers = sharingProgressWithTeamMembers;
    }

    public Map<UUID, TeamMember> getMembers() {
        return membership.members();
    }

    @Override
    public void grantQuestCompletion(Quest quest) {
        grantQuestCompletion(quest.questId());
    }

    @Override
    public void grantQuestCompletion(UUID uuid) {
        recordQuestCompletion(uuid);
    }

    @Override
    public void handleReward(Quest quest) {
    }

    @Override
    public boolean hasCompletedQuest(Quest quest) {
        return isQuestCompleted(quest.questId());
    }

    @Override
    public boolean hasCompletedObjective(QuestObjective<?, ?> objective) {
        return isObjectiveCompleted(objective.getObjectiveId());
    }

    @Override
    public boolean containsPlayer(UUID playerId) {
        return getMembers().containsKey(playerId);
    }

    @Override
    public Set<UUID> getClaimedRewards(UUID playerId) {
        TeamMember member = getMembers().get(playerId);
        return member == null ? Set.of() : member.claimedRewards();
    }

    @Override
    public boolean tryClaimReward(RewardType<?> reward, ServerPlayer player) {
        return containsPlayer(player.getUUID()) && super.tryClaimReward(reward, player);
    }

    @Override
    public synchronized void resetQuestProgress(Quest quest) {
        super.resetQuestProgress(quest);
        for (TeamMember member : getMembers().values()) {
            quest.rewards().forEach(reward -> member.claimedRewards().remove(reward.getRewardId()));
        }
    }

    @Override
    public PlayerUser getClientPlayerUser(UUID uuid) {
        return buildPlayerUser(uuid);
    }

    private PlayerUser buildPlayerUser(UUID uuid) {
        Set<UUID> rewards = getClaimedRewards(uuid);
        return new PlayerUser(uuid, getCompletedQuests(), getCompletedObjectives(), rewards, getQuestObjectiveProgress());
    }
}
