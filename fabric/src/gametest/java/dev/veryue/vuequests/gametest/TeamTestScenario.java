package dev.veryue.vuequests.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.TeamActionException;
import dev.veryue.vuequests.UserManager;
import dev.veryue.vuequests.data.FlatFileUserLoader;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.TeamUser;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.ObjectiveSubmitItem;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.QuestPosition;
import dev.veryue.vuequests.models.reward.RewardExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ReportedException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class TeamTestScenario implements AutoCloseable {
    final GameTestHelper helper;
    final MinecraftServer server;
    final Path directory;
    final TeamTestUserLoader<PlayerUser> players;
    final TeamTestUserLoader<TeamUser> teams;
    final List<String> writes = new CopyOnWriteArrayList<>();
    final List<ServerPlayer> online = new ArrayList<>();
    final ObjectiveSubmitItem objective = new ObjectiveSubmitItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND), 100);
    final RewardExperience reward = new RewardExperience(UUID.randomUUID(), 10);
    final Quest quest = new Quest(new QuestPosition(0, 0), new QuestDisplay("", "", "", Items.BOOK, ""),
            UUID.randomUUID(), UUID.randomUUID(), List.of(objective), List.of(reward), List.of());
    final ServerPlayer owner;
    final ServerPlayer member;
    final ServerPlayer other;
    UserManager manager;
    boolean expectedFailure;

    TeamTestScenario(GameTestHelper helper) {
        this.helper = helper;
        server = helper == null ? TeamTestEnvironment.level.get().getServer() : helper.getLevel().getServer();
        try {
            Path root = server.getWorldPath(LevelResource.ROOT).resolve("team-verification");
            Files.createDirectories(root);
            directory = Files.createTempDirectory(root, "case-").toAbsolutePath();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        players = new TeamTestUserLoader<>(new FlatFileUserLoader<>(directory, "users", PlayerUser.CODEC,
                () -> server.registryAccess().createSerializationContext(JsonOps.INSTANCE)), "player", writes);
        teams = new TeamTestUserLoader<>(new FlatFileUserLoader<>(directory, "teams", TeamUser.CODEC,
                () -> server.registryAccess().createSerializationContext(JsonOps.INSTANCE)), "team", writes);
        TeamFailureCapture.enable(directory);
        manager = new UserManager(server, players, teams);
        owner = login(UUID.randomUUID(), "owner");
        member = login(UUID.randomUUID(), "member");
        other = login(UUID.randomUUID(), "other");
    }

    ServerPlayer login(UUID id, String prefix) {
        String name = (prefix + id.toString().replace("-", "")).substring(0, 16);
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        var player = new ServerPlayer(server, helper == null ? server.overworld() : helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        online.add(player);
        manager.loadPlayer(player);
        return player;
    }

    void logout(ServerPlayer player) {
        manager.unloadPlayer(player.getUUID());
        player.connection.disconnect(Component.empty());
        server.getPlayerList().remove(player);
        online.remove(player);
    }

    TeamUser create() {
        manager.createTeam(owner);
        return manager.getPlayerTeam(owner.getUUID()).orElseThrow();
    }

    void join(ServerPlayer player, TeamUser team) {
        manager.invite(team.getOwnerId(), team.getUuid(), player.getGameProfile().name());
        manager.respondToInvitation(player, team.getUuid(), true);
    }

    TeamUser team() { return manager.getPlayerTeam(owner.getUUID()).orElseThrow(); }
    User active(ServerPlayer player) { return manager.getUser(player.getUUID()).orElseThrow(); }

    void seed(ServerPlayer player, int amount) {
        active(player).getQuestObjectiveProgress().put(objective.getObjectiveId(),
                new QuestObjectiveProgress<>(objective.getObjectiveId(), objective.getType(), amount));
    }

    int progress(ServerPlayer player) { return progress(active(player)); }
    int progress(User user) {
        var value = user.getObjectiveProgress(objective);
        return value == null || value.getCurrentProgress() == null ? 0 : value.getCurrentProgress();
    }

    PlayerUser saved(ServerPlayer player) {
        try { return players.loadUser(player.getUUID()); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    void flush() { manager.saveAllUsers().join(); }

    void restart() {
        manager.stop();
        manager = new UserManager(server, players, teams);
        for (ServerPlayer player : online) manager.loadPlayer(player);
    }

    void equal(Object actual, Object expected, String description) {
        if (helper != null) helper.assertValueEqual(actual, expected, description);
        else if (!Objects.equals(actual, expected)) throw new AssertionError(description + ": expected " + expected + ", got " + actual);
    }

    void check(boolean value, String description) {
        if (helper != null) helper.assertTrue(value, description);
        else if (!value) throw new AssertionError(description);
    }

    void rejects(Runnable action) {
        try { action.run(); }
        catch (TeamActionException exception) { return; }
        throw new AssertionError("Expected team validation rejection");
    }

    void fatal(Runnable action) {
        expectedFailure = true;
        try { action.run(); }
        catch (RuntimeException exception) {
            Throwable cause = exception;
            while (cause.getCause() != null && !(cause instanceof ReportedException)) cause = cause.getCause();
            if (!(cause instanceof ReportedException)) throw exception;
            check(manager.hasFatalFailure(), "fatal failure latch");
            check(TeamFailureCapture.report(directory) != null, "native crash handoff");
            return;
        }
        throw new AssertionError("Expected fatal persistence failure");
    }

    static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    @SuppressWarnings("unchecked")
    Map<UUID, PlayerUser> personalCache() { return (Map<UUID, PlayerUser>) field(manager, "players"); }

    @Override
    public void close() {
        if (players.release != null) players.release.countDown();
        try {
            try { manager.stop(); }
            catch (ReportedException exception) { if (!expectedFailure) throw exception; }
        } finally {
            for (ServerPlayer player : new ArrayList<>(online)) {
                player.connection.disconnect(Component.empty());
                server.getPlayerList().remove(player);
            }
            online.clear();
            TeamFailureCapture.disable(directory);
        }
    }
}
