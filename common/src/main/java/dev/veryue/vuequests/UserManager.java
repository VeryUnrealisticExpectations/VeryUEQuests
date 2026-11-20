package dev.veryue.vuequests;

import com.mojang.serialization.Codec;
import dev.veryue.vuequests.data.FlatFileUserLoader;
import dev.veryue.vuequests.data.UserLoader;
import dev.veryue.vuequests.exceptions.UserDoesNotExitException;
import dev.veryue.vuequests.models.*;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UserManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VeryUEConstants.MOD_ID);
    private final MinecraftServer server;
    private final UserLoader<PlayerUser> playerLoader;
    private final UserLoader<TeamUser> teamLoader;
    private final Map<UUID, PlayerUser> players = new HashMap<>();
    private final Map<UUID, Component> unavailable = new HashMap<>();
    private final Map<UUID, TeamUser> teamsByPlayer = new HashMap<>();
    private final Map<UUID, TeamUser> teamsById = new HashMap<>();
    private final Map<UUID, Set<UUID>> invitations = new HashMap<>();
    private final ScheduledExecutorService saveExecutor;
    private final ScheduledFuture<?> autosave;
    private volatile ReportedException fatalFailure;
    private volatile boolean running = true;

    public UserManager(MinecraftServer server) {
        this(server, new FlatFileUserLoader<>(server, "users", PlayerUser.CODEC),
                new FlatFileUserLoader<>(server, "teams", TeamUser.CODEC));
    }

    public UserManager(MinecraftServer server, UserLoader<PlayerUser> playerLoader, UserLoader<TeamUser> teamLoader) {
        this.server = server;
        this.playerLoader = playerLoader;
        this.teamLoader = teamLoader;
        Map<UUID, TeamUser> loaded = teamLoader.loadAllUsers();
        Map<UUID, TeamUser> index = new HashMap<>();
        for (TeamUser team : loaded.values()) {
            if (!team.containsPlayer(team.getOwnerId())) {
                throw new IllegalStateException("Team owner is not a member: " + teamLoader.userPath(team.getUuid()));
            }
            for (UUID member : team.getMembers().keySet()) {
                TeamUser previous = index.putIfAbsent(member, team);
                if (previous != null) {
                    throw new IllegalStateException("Player " + member + " belongs to multiple teams: "
                            + teamLoader.userPath(previous.getUuid()) + " and " + teamLoader.userPath(team.getUuid()));
                }
            }
        }
        teamsById.putAll(loaded);
        teamsByPlayer.putAll(index);
        saveExecutor = Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "VUEQuests user saves"));
        autosave = saveExecutor.scheduleAtFixedRate(() -> {
            if (isRunning(server)) {
                saveAllUsers();
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    private void requireRunning() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        if (!running) {
            throw new IllegalStateException("Quest data service is stopped");
        }
    }

    public boolean isRunning(MinecraftServer server) {
        return running && fatalFailure == null && this.server == server;
    }

    public boolean hasFatalFailure() {
        return fatalFailure != null;
    }

    public boolean isReady(UUID playerId) {
        return running && fatalFailure == null && players.containsKey(playerId) && !unavailable.containsKey(playerId);
    }

    public Component dataStatus(UUID playerId) {
        return unavailable.getOrDefault(playerId, Component.translatable("team.vuequests.error.data_unavailable"));
    }

    public Optional<User> getUser(UUID playerId) {
        if (!isReady(playerId)) {
            return Optional.empty();
        }
        TeamUser team = teamsByPlayer.get(playerId);
        return Optional.of(team != null && team.isSharingProgressWithTeamMembers() ? team : players.get(playerId));
    }

    public User getUser(ServerPlayer player) {
        return getUser(player.getUUID()).orElseThrow(() -> new TeamActionException("data_unavailable"));
    }

    public Optional<TeamUser> getTeam(UUID teamId) {
        return Optional.ofNullable(teamsById.get(teamId));
    }

    public Optional<TeamUser> getPlayerTeam(UUID playerId) {
        return Optional.ofNullable(teamsByPlayer.get(playerId));
    }

    public Set<UUID> getProgressRecipients(UUID playerId) {
        if (!isReady(playerId)) {
            return Set.of();
        }
        TeamUser team = teamsByPlayer.get(playerId);
        return team != null && team.isSharingProgressWithTeamMembers() ? new HashSet<>(team.getMembers().keySet()) : Set.of(playerId);
    }

    public void loadPlayer(ServerPlayer player) {
        requireRunning();
        UUID id = player.getUUID();
        if (players.containsKey(id) || unavailable.containsKey(id)) {
            return;
        }
        awaitPendingSaves();
        PlayerUser personal;
        try {
            personal = playerLoader.loadUser(id);
        } catch (UserDoesNotExitException exception) {
            if (teamsByPlayer.containsKey(id)) {
                markUnavailable(id, exception);
                return;
            }
            personal = new PlayerUser(id);
        } catch (RuntimeException exception) {
            markUnavailable(id, exception);
            return;
        }
        TeamUser team = teamsByPlayer.get(id);
        if (team != null && !team.getMembers().get(id).lastKnownName().equals(player.getGameProfile().name())) {
            TeamUser candidate = userSnapshot(team, TeamUser.CODEC, teamLoader);
            candidate.getMembers().put(id, new TeamMember(player.getGameProfile().name(), team.getClaimedRewards(id)));
            saveData("refresh member name", Map.of(), candidate, null);
            addToTeam(Map.of(), candidate, null);
        }
        players.put(id, personal);
    }

    private void markUnavailable(UUID id, Exception exception) {
        LOGGER.error("Quest data unavailable for member {} at {}", id, playerLoader.userPath(id), exception);
        unavailable.put(id, Component.translatable("team.vuequests.error.personal_file", id.toString()));
    }

    private void requireReady(UUID id) {
        requireRunning();
        if (!isReady(id)) {
            throw new TeamActionException("data_unavailable");
        }
    }

    private PlayerUser personalForTransition(UUID id) {
        if (unavailable.containsKey(id)) {
            throw new TeamActionException("member_unavailable", id.toString());
        }
        PlayerUser personal = players.get(id);
        if (personal != null) {
            return personal;
        }
        awaitPendingSaves();
        try {
            return playerLoader.loadUser(id);
        } catch (UserDoesNotExitException | RuntimeException exception) {
            LOGGER.error("Cannot transition member {}: personal file {} is unavailable", id, playerLoader.userPath(id), exception);
            if (server.getPlayerList().getPlayer(id) != null) {
                markUnavailable(id, exception);
            }
            throw new TeamActionException("member_unavailable", id.toString());
        }
    }

    private Map<UUID, PlayerUser> personalCandidates(Collection<UUID> ids) {
        Map<UUID, PlayerUser> sources = new HashMap<>();
        for (UUID id : ids.stream().sorted().toList()) sources.put(id, personalForTransition(id));
        return playerSnapshots(sources);
    }

    private TeamUser memberTeam(UUID requester, UUID teamId) {
        requireReady(requester);
        TeamUser team = teamsById.get(teamId);
        if (team == null || teamsByPlayer.get(requester) != team) {
            throw new TeamActionException("stale_team");
        }
        return team;
    }

    private TeamUser ownedTeam(UUID requester, UUID teamId) {
        TeamUser team = memberTeam(requester, teamId);
        if (!team.getOwnerId().equals(requester)) {
            throw new TeamActionException("owner_only");
        }
        return team;
    }

    public Set<UUID> createTeam(ServerPlayer requester) {
        UUID owner = requester.getUUID();
        requireReady(owner);
        if (teamsByPlayer.containsKey(owner)) {
            throw new TeamActionException("already_teamed");
        }
        Map<UUID, PlayerUser> personal = personalCandidates(Set.of(owner));
        String ownerName = requester.getGameProfile().name();
        TeamUser team = new TeamUser(UUID.randomUUID(), ownerName + "'s Team", owner, ownerName);
        replaceProgress(team, personal.get(owner));
        replaceClaims(team.getClaimedRewards(owner), personal.get(owner).getClaimedRewards());
        saveData("create team", personal, team, null);
        addToTeam(personal, team, null);
        invitations.remove(owner);
        return Set.of(owner);
    }

    public Set<UUID> renameTeam(UUID requester, UUID teamId, String requestedName) {
        TeamUser team = ownedTeam(requester, teamId);
        String name = requestedName.strip();
        if (name.isBlank() || name.length() > 64) {
            throw new TeamActionException("invalid_name");
        }
        if (name.equals(team.getName())) {
            return Set.of(requester);
        }
        TeamUser candidate = userSnapshot(team, TeamUser.CODEC, teamLoader);
        candidate.setName(name);
        saveData("rename team", Map.of(), candidate, null);
        addToTeam(Map.of(), candidate, null);
        return teamAudience(candidate);
    }

    public Set<UUID> changeSetting(UUID requester, UUID teamId, TeamSetting setting, boolean value) {
        TeamUser team = ownedTeam(requester, teamId);
        boolean current = setting == TeamSetting.SHARING_PROGRESS ? team.isSharingProgressWithTeamMembers() : team.isKeepProgressWhenLeavingTeam();
        if (current == value) {
            return Set.of(requester);
        }
        Map<UUID, PlayerUser> personal = setting == TeamSetting.SHARING_PROGRESS
                ? personalCandidates(team.getMembers().keySet()) : Map.of();
        TeamUser candidate = userSnapshot(team, TeamUser.CODEC, teamLoader);
        if (setting == TeamSetting.KEEP_PROGRESS) {
            candidate.setKeepProgressWhenLeavingTeam(value);
        } else {
            if (value) {
                replaceProgress(candidate, personal.get(candidate.getOwnerId()));
                personal.forEach((id, user) -> replaceClaims(candidate.getClaimedRewards(id), user.getClaimedRewards()));
            } else {
                personal.forEach((id, user) -> {
                    replaceProgress(user, team);
                    replaceClaims(user.getClaimedRewards(), team.getClaimedRewards(id));
                });
            }
            candidate.setSharingProgressWithTeamMembers(value);
        }
        saveData("change team setting " + setting, personal, candidate, null);
        addToTeam(personal, candidate, null);
        return new HashSet<>(candidate.getMembers().keySet());
    }

    public Set<UUID> invite(UUID requester, UUID teamId, String targetName) {
        ownedTeam(requester, teamId);
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            throw new TeamActionException("offline_player", targetName);
        }
        requireReady(target.getUUID());
        if (teamsByPlayer.containsKey(target.getUUID())) {
            throw new TeamActionException("target_teamed", targetName);
        }
        invitations.computeIfAbsent(target.getUUID(), id -> new LinkedHashSet<>()).add(teamId);
        return Set.of(requester, target.getUUID());
    }

    public Collection<TeamUser> getInvitations(UUID recipient) {
        return invitations.getOrDefault(recipient, Set.of()).stream().map(teamsById::get).filter(Objects::nonNull).toList();
    }

    public Set<UUID> respondToInvitation(ServerPlayer requester, UUID teamId, boolean accept) {
        UUID id = requester.getUUID();
        requireReady(id);
        Set<UUID> pending = invitations.get(id);
        if (pending == null || !pending.contains(teamId)) {
            throw new TeamActionException("invalid_invitation");
        }
        TeamUser team = teamsById.get(teamId);
        if (team == null) {
            pending.remove(teamId);
            throw new TeamActionException("invalid_invitation");
        }
        if (!accept) {
            pending.remove(teamId);
            return Set.of(id);
        }
        if (teamsByPlayer.containsKey(id)) {
            throw new TeamActionException("already_teamed");
        }
        Map<UUID, PlayerUser> personal = personalCandidates(Set.of(id));
        TeamUser candidate = userSnapshot(team, TeamUser.CODEC, teamLoader);
        candidate.getMembers().put(id, new TeamMember(requester.getGameProfile().name(), personal.get(id).getClaimedRewards()));
        saveData("accept invitation", personal, candidate, null);
        addToTeam(personal, candidate, null);
        invitations.remove(id);
        return new HashSet<>(candidate.getMembers().keySet());
    }

    public Set<UUID> leaveTeam(UUID requester, UUID teamId) {
        TeamUser team = memberTeam(requester, teamId);
        return removeFromTeam(team, team.getOwnerId().equals(requester) ? new HashSet<>(team.getMembers().keySet()) : Set.of(requester));
    }

    public Set<UUID> kickMember(UUID requester, UUID teamId, UUID target) {
        TeamUser team = ownedTeam(requester, teamId);
        if (!team.containsPlayer(target) || target.equals(team.getOwnerId())) {
            throw new TeamActionException("invalid_member");
        }
        return removeFromTeam(team, Set.of(target));
    }

    private Set<UUID> removeFromTeam(TeamUser team, Set<UUID> departing) {
        Map<UUID, PlayerUser> personal = personalCandidates(departing);
        boolean disband = departing.contains(team.getOwnerId());
        TeamUser candidate = userSnapshot(team, TeamUser.CODEC, teamLoader);
        for (UUID id : departing) {
            PlayerUser user = personal.get(id);
            if (team.isSharingProgressWithTeamMembers()) {
                if (team.isKeepProgressWhenLeavingTeam()) {
                    replaceProgress(user, team);
                }
                replaceClaims(user.getClaimedRewards(), team.getClaimedRewards(id));
            }
            candidate.getMembers().remove(id);
        }
        Set<UUID> affected = teamAudience(team);
        saveData(disband ? "disband team" : "depart team", personal, disband ? null : candidate, disband ? team.getUuid() : null);
        addToTeam(personal, disband ? null : candidate, disband ? team.getUuid() : null);
        if (disband) {
            invitations.values().forEach(pending -> pending.remove(team.getUuid()));
        }
        return affected;
    }

    private Set<UUID> teamAudience(TeamUser team) {
        Set<UUID> affected = new HashSet<>(team.getMembers().keySet());
        invitations.forEach((recipient, pending) -> {
            if (pending.contains(team.getUuid())) {
                affected.add(recipient);
            }
        });
        return affected;
    }

    private void replaceProgress(User destination, User source) {
        User detached = source instanceof TeamUser team ? userSnapshot(team, TeamUser.CODEC, teamLoader)
                : userSnapshot((PlayerUser) source, PlayerUser.CODEC, playerLoader);
        destination.getCompletedQuests().clear();
        destination.getCompletedQuests().addAll(detached.getCompletedQuests());
        destination.getCompletedObjectives().clear();
        destination.getCompletedObjectives().addAll(detached.getCompletedObjectives());
        destination.getQuestObjectiveProgress().clear();
        destination.getQuestObjectiveProgress().putAll(detached.getQuestObjectiveProgress());
    }

    private void replaceClaims(Set<UUID> destination, Set<UUID> source) {
        destination.clear();
        destination.addAll(source);
    }

    private <T extends User> T userSnapshot(T user, Codec<T> codec, UserLoader<T> loader) {
        requireRunning();
        try {
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            return codec.encodeStart(ops, user).flatMap(encoded -> codec.parse(ops, encoded)).getOrThrow();
        } catch (RuntimeException exception) {
            throw fail("prepare required save for " + user.getUuid(), loader.userPath(user.getUuid()), exception);
        }
    }

    private Map<UUID, PlayerUser> playerSnapshots(Map<UUID, PlayerUser> sources) {
        Map<UUID, PlayerUser> snapshots = new HashMap<>();
        sources.forEach((id, user) -> snapshots.put(id, userSnapshot(user, PlayerUser.CODEC, playerLoader)));
        return snapshots;
    }

    private void saveData(String operation, Map<UUID, PlayerUser> personal, TeamUser team, UUID deleteTeam) {
        awaitSave(saveDataAsync(operation, personal, team, deleteTeam));
    }

    private CompletableFuture<Void> saveDataAsync(String operation, Map<UUID, PlayerUser> personal, TeamUser team, UUID deleteTeam) {
        requireRunning();
        Map<UUID, PlayerUser> savedPlayers = playerSnapshots(personal);
        TeamUser savedTeam = team == null ? null : userSnapshot(team, TeamUser.CODEC, teamLoader);
        return CompletableFuture.runAsync(() -> writeData(operation, savedPlayers, savedTeam, deleteTeam), saveExecutor);
    }

    private void writeData(String operation, Map<UUID, PlayerUser> personal, TeamUser team, UUID deleteTeam) {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        Path path = null;
        try {
            for (UUID id : personal.keySet().stream().sorted().toList()) {
                if (fatalFailure != null) {
                    throw fatalFailure;
                }
                path = playerLoader.userPath(id);
                playerLoader.saveUser(personal.get(id));
            }
            if (team != null) {
                if (fatalFailure != null) {
                    throw fatalFailure;
                }
                path = teamLoader.userPath(team.getUuid());
                teamLoader.saveUser(team);
            }
            if (deleteTeam != null) {
                if (fatalFailure != null) {
                    throw fatalFailure;
                }
                path = teamLoader.userPath(deleteTeam);
                teamLoader.deleteUser(deleteTeam);
            }
        } catch (RuntimeException exception) {
            throw fail(operation + "; players=" + personal.keySet() + "; team=" + (team == null ? deleteTeam : team.getUuid()), path, exception);
        }
    }

    private synchronized ReportedException fail(String operation, Path path, RuntimeException exception) {
        if (fatalFailure == null) {
            CrashReport report = CrashReport.forThrowable(exception, "VUEQuests persistence failure");
            CrashReportCategory category = report.addCategory("Quest persistence");
            category.setDetail("Operation", operation);
            category.setDetail("File", String.valueOf(path));
            fatalFailure = new ReportedException(report);
            server.delayCrash(report);
            LOGGER.error("Fatal quest persistence failure during {} at {}", operation, path, exception);
        }
        return fatalFailure;
    }

    private void addToTeam(Map<UUID, PlayerUser> personal, TeamUser team, UUID deleteTeam) {
        UUID teamId = team == null ? deleteTeam : team.getUuid();
        if (teamId != null) {
            TeamUser previous = teamsById.remove(teamId);
            if (previous != null) {
                previous.getMembers().keySet().forEach(teamsByPlayer::remove);
            }
            if (team != null) {
                teamsById.put(teamId, team);
                team.getMembers().keySet().forEach(id -> teamsByPlayer.put(id, team));
            }
        }
        personal.forEach((id, user) -> {
            if (players.containsKey(id)) {
                players.put(id, user);
            }
        });
    }

    public void unloadPlayer(UUID id) {
        if (fatalFailure != null) {
            return;
        }
        invitations.remove(id);
        PlayerUser personal = players.get(id);
        TeamUser team = teamsByPlayer.get(id);
        if (running && personal != null) {
            saveDataAsync("logout " + id, Map.of(id, personal),
                    team != null && team.isSharingProgressWithTeamMembers() ? team : null, null);
        }
        players.remove(id);
        unavailable.remove(id);
    }

    public CompletableFuture<Void> saveAllUsers() {
        return saveAllUsers("autosave");
    }

    private CompletableFuture<Void> saveAllUsers(String operation) {
        requireRunning();
        Map<UUID, PlayerUser> savedPlayers = playerSnapshots(players);
        List<TeamUser> savedTeams = new ArrayList<>();
        for (UUID id : teamsById.keySet().stream().sorted().toList()) {
            savedTeams.add(userSnapshot(teamsById.get(id), TeamUser.CODEC, teamLoader));
        }
        return CompletableFuture.runAsync(() -> {
            writeData(operation + " personal records", savedPlayers, null, null);
            for (TeamUser team : savedTeams) writeData(operation + " team " + team.getUuid(), Map.of(), team, null);
        }, saveExecutor);
    }

    private void awaitPendingSaves() {
        awaitSave(CompletableFuture.runAsync(() -> {
            if (fatalFailure != null) {
                throw fatalFailure;
            }
        }, saveExecutor));
    }

    private void awaitSave(CompletableFuture<Void> save) {
        try {
            save.join();
        } catch (CompletionException exception) {
            if (fatalFailure != null) {
                throw fatalFailure;
            }
            throw exception;
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        autosave.cancel(false);
        try {
            awaitPendingSaves();
            awaitSave(saveAllUsers("shutdown"));
        } finally {
            running = false;
            saveExecutor.close();
            if (fatalFailure != null) {
                CrashReport report = fatalFailure.getReport();
                Path path = server.getServerDirectory().resolve("crash-reports").resolve("crash-vuequests-" + System.currentTimeMillis() + "-server.txt");
                report.saveToFile(path, ReportType.CRASH);
                server.delayCrash(report);
                LOGGER.error("Quest persistence failed; crash report: {}", path, fatalFailure);
            } else {
                players.clear();
                unavailable.clear();
                teamsById.clear();
                teamsByPlayer.clear();
                invitations.clear();
            }
        }
        if (fatalFailure != null) {
            throw fatalFailure;
        }
    }
}
