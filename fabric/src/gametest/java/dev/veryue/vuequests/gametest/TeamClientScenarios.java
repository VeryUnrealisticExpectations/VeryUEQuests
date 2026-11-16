package dev.veryue.vuequests.gametest;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.ClientTeamData;
import dev.veryue.vuequests.client.VeryUEKeyMappings;
import dev.veryue.vuequests.client.screens.QuestSettingsScreen;
import dev.veryue.vuequests.client.widget.panels.TeamSettingsPanel;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.TeamSetting;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.models.objective.ObjectiveCollectItem;
import dev.veryue.vuequests.models.reward.RewardAttribute;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.network.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class TeamClientScenarios {
    final ClientGameTestContext context;
    final TestServerContext server;
    final TestServerConnection connection;
    final String environment;
    final List<Map<String, String>> results = new ArrayList<>();

    TeamClientScenarios(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String environment) {
        this.context = context;
        this.server = server;
        this.connection = connection;
        this.environment = environment;
    }

    int run() {
        boolean dedicated = environment.equals("dedicated");
        QuestTestAssertions.equal(server.computeOnServer(instance -> instance.isDedicatedServer()), dedicated);
        context.runOnClient(client -> {
            QuestTestAssertions.equal(client.getSingleplayerServer() == null, dedicated);
            QuestTestAssertions.equal(client.getConnection().getConnection().isMemoryConnection(), !dedicated);
        });
        sharedScenarios();
        check("network_owner_actions", this::ownerActions);
        check("network_invitation_session_and_rename", this::invitations);
        check("network_deny_and_clear_other_invitations", this::denyAndCreate);
        check("request_ids_and_pending_controls", this::pendingCorrelation);
        check("distributed_progress_and_personal_updates", this::distribution);
        check("reward_delivery_reclaim_and_admin_reset", this::rewardHistory);
        check("offline_roster_and_recipient_projection", this::rosterAndProjection);
        check("unavailable_data_and_reload_no_retry", this::unavailableData);
        check("native_rechecks_and_attribute_restoration", this::rechecksAndAttributes);
        check("settings_drafts_and_disband_confirmation", this::settingsControls);
        check("failed_transition_has_no_success_ack", this::failedRequest);
        writeReport();
        return (int) results.stream().filter(result -> result.get("status").equals("failed")).count();
    }

    void check(String name, Runnable action) {
        var result = new LinkedHashMap<String, String>();
        result.put("name", name);
        try {
            action.run();
            result.put("status", "passed");
        } catch (Throwable exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) cause = cause.getCause();
            result.put("status", "failed");
            result.put("error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
        results.add(result);
    }

    void sharedScenarios() {
        for (Class<?> type : List.of(TeamTransitionGameTests.class, TeamInvitationGameTests.class, TeamPersistenceGameTests.class)) {
            List<Method> methods = Arrays.stream(type.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(GameTest.class))
                    .sorted(Comparator.comparing(Method::getName)).toList();
            for (Method method : methods) {
                check("server_contract/" + method.getName(), () -> server.runOnServer(instance -> {
                    TeamTestEnvironment.level.set(connection.getServerPlayer().level());
                    try {
                        Object suite = type.getDeclaredConstructor().newInstance();
                        method.invoke(suite, new Object[]{null});
                    } catch (InvocationTargetException exception) {
                        Throwable cause = exception.getCause();
                        if (cause instanceof Error error) throw error;
                        if (cause instanceof RuntimeException runtime) throw runtime;
                        throw new IllegalStateException(cause);
                    } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
                    finally { TeamTestEnvironment.level.remove(); }
                }));
                context.waitTicks(1);
            }
        }
    }

    TeamClientScenario scenario() { return new TeamClientScenario(context, server, connection); }
    UUID teamId() { return context.computeOnClient(client -> ClientTeamData.getTeam().id()); }

    void ownerActions() {
        try (var s = scenario()) {
            s.request(CreateTeamPayload::new, true);
            UUID team = teamId();
            UUID owner = s.playerId();
            context.runOnClient(client -> QuestTestAssertions.equal(ClientTeamData.getTeam().ownerId(), owner));
            s.request(id -> new RenameTeamPayload(id, team, "  Network Team  "), true);
            context.runOnClient(client -> QuestTestAssertions.equal(ClientTeamData.getTeam().name(), "Network Team"));
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.KEEP_PROGRESS, false), true);
            context.runOnClient(client -> {
                QuestTestAssertions.check(!ClientTeamData.getTeam().keepProgressWhenLeavingTeam());
                QuestTestAssertions.check(ClientTeamData.getTeam().sharingProgressWithTeamMembers());
            });
            s.request(id -> new InviteTeamPlayerPayload(id, team, s.state.member.getGameProfile().name()), true);
            s.onPlayer(player -> s.state.manager.respondToInvitation(s.state.member, team, true));
            s.refresh();
            context.runOnClient(client -> QuestTestAssertions.equal(ClientTeamData.getTeam().members().size(), 2));
            s.onPlayer(player -> s.state.logout(s.state.member));
            s.request(id -> new KickTeamMemberPayload(id, team, s.state.member.getUUID()), true);
            context.runOnClient(client -> QuestTestAssertions.equal(ClientTeamData.getTeam().members().size(), 1));
            s.request(id -> new RenameTeamPayload(id, UUID.randomUUID(), "Forged"), false);
            context.runOnClient(client -> QuestTestAssertions.equal(ClientTeamData.getTeam().id(), team));
            s.request(id -> new LeaveTeamPayload(id, team), true);
            context.runOnClient(client -> QuestTestAssertions.check(ClientTeamData.getTeam() == null));
        }
    }

    void invitations() {
        try (var s = scenario()) {
            UUID team = s.fromPlayer(player -> {
                var source = s.state.create();
                VeryUEMod.INSTANCE.handleTeamRequest(s.state.owner, new InviteTeamPlayerPayload(UUID.randomUUID(), source.getUuid(), player.getGameProfile().name()));
                return source.getUuid();
            });
            context.waitFor(client -> ClientTeamData.getInvitations().size() == 1);
            s.onPlayer(player -> VeryUEMod.INSTANCE.handleTeamRequest(s.state.owner,
                    new RenameTeamPayload(UUID.randomUUID(), team, "Renamed Invitation")));
            context.waitFor(client -> ClientTeamData.getInvitations().iterator().next().teamName().equals("Renamed Invitation"));
            s.onPlayer(player -> s.state.logout(s.state.owner));
            s.request(id -> new RespondTeamInvitationPayload(id, team, true), true);
            context.runOnClient(client -> {
                QuestTestAssertions.equal(ClientTeamData.getTeam().id(), team);
                QuestTestAssertions.check(ClientTeamData.getInvitations().isEmpty());
            });
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.SHARING_PROGRESS, false), false);
            s.request(id -> new LeaveTeamPayload(id, team), true);
        }
    }

    void denyAndCreate() {
        try (var s = scenario()) {
            UUID team = s.fromPlayer(player -> {
                var source = s.state.create();
                VeryUEMod.INSTANCE.handleTeamRequest(s.state.owner, new InviteTeamPlayerPayload(UUID.randomUUID(), source.getUuid(), player.getGameProfile().name()));
                return source.getUuid();
            });
            context.waitFor(client -> !ClientTeamData.getInvitations().isEmpty());
            s.request(id -> new RespondTeamInvitationPayload(id, team, false), true);
            context.runOnClient(client -> QuestTestAssertions.check(ClientTeamData.getInvitations().isEmpty()));
            s.onPlayer(player -> VeryUEMod.INSTANCE.handleTeamRequest(s.state.owner,
                    new InviteTeamPlayerPayload(UUID.randomUUID(), team, player.getGameProfile().name())));
            context.waitFor(client -> !ClientTeamData.getInvitations().isEmpty());
            s.request(CreateTeamPayload::new, true);
            context.runOnClient(client -> QuestTestAssertions.check(ClientTeamData.getInvitations().isEmpty()));
            s.request(id -> new RespondTeamInvitationPayload(id, team, true), false);
        }
    }

    void pendingCorrelation() {
        try (var s = scenario()) {
            openSettings();
            var pending = new AtomicReference<TeamRequestPayload>();
            context.runOnClient(client -> {
                ClientTeamData.request(CreateTeamPayload::new, payload -> pending.set((TeamRequestPayload) payload));
                ClientTeamData.request(CreateTeamPayload::new, payload -> { throw new AssertionError("Second pending action sent"); });
                QuestTestAssertions.check(ClientTeamData.getTeam() == null);
            });
            context.waitTicks(2);
            context.runOnClient(client -> QuestTestAssertions.check(teamPanel().children().stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).noneMatch(button -> button.active)));
            s.onPlayer(player -> {
                VeryUEMod.INSTANCE.syncTeamState(player);
                ServerPlayNetworking.send(player, new TeamActionResultPayload(UUID.randomUUID(), true, Component.literal("unrelated")));
            });
            connection.waitForClientboundPackets();
            context.runOnClient(client -> QuestTestAssertions.check(ClientTeamData.isPending()));
            s.send(pending.get());
            context.waitFor(client -> !ClientTeamData.isPending() && ClientTeamData.getTeam() != null);
        }
    }

    void distribution() {
        try (var s = scenario()) {
            s.request(CreateTeamPayload::new, true);
            UUID team = teamId();
            s.onPlayer(player -> {
                s.state.manager.invite(player.getUUID(), team, s.state.member.getGameProfile().name());
                s.state.manager.respondToInvitation(s.state.member, team, true);
                s.active(player).getQuestObjectiveProgress().put(s.state.objective.getObjectiveId(),
                        new QuestObjectiveProgress<>(s.state.objective.getObjectiveId(), s.state.objective.getType(), 60));
                s.state.logout(s.state.member);
            });
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.KEEP_PROGRESS, false), true);
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.SHARING_PROGRESS, false), true);
            context.runOnClient(client -> QuestTestAssertions.equal(ClientQuestData.getUser().getObjectiveProgress(s.state.objective).getCurrentProgress(), 60));
            s.onPlayer(player -> {
                QuestTestAssertions.equal(s.state.progress(s.state.saved(s.state.member)), 60);
                s.active(player).getObjectiveProgress(s.state.objective).setCurrentProgress(70);
                QuestTestAssertions.equal(s.state.progress(s.state.manager.getTeam(team).orElseThrow()), 60);
            });
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.SHARING_PROGRESS, true), true);
            context.runOnClient(client -> QuestTestAssertions.equal(ClientQuestData.getUser().getObjectiveProgress(s.state.objective).getCurrentProgress(), 70));
        }
    }

    void rewardHistory() {
        try (var s = scenario()) {
            int baseline = s.fromPlayer(player -> player.totalExperience);
            s.onPlayer(player -> s.active(player).completeQuestProgress(s.state.quest));
            s.refresh();
            s.send(new ClaimQuestRewardPayload(s.state.quest.questId(), s.state.reward.getRewardId()));
            context.waitFor(client -> ClientQuestData.isRewardClaimed(s.state.reward));
            QuestTestAssertions.equal(s.fromPlayer(player -> player.totalExperience), baseline + 10);
            s.request(CreateTeamPayload::new, true);
            UUID team = teamId();
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.SHARING_PROGRESS, false), true);
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.SHARING_PROGRESS, true), true);
            s.request(id -> new LeaveTeamPayload(id, team), true);
            s.send(new ClaimQuestRewardPayload(s.state.quest.questId(), s.state.reward.getRewardId()));
            QuestTestAssertions.equal(s.fromPlayer(player -> player.totalExperience), baseline + 10);
            s.onPlayer(player -> s.active(player).resetQuestProgress(s.state.quest));
            s.request(CreateTeamPayload::new, true);
            context.runOnClient(client -> QuestTestAssertions.check(!ClientQuestData.isRewardClaimed(s.state.reward)));
            s.onPlayer(player -> s.active(player).completeQuestProgress(s.state.quest));
            s.send(new ClaimQuestRewardPayload(s.state.quest.questId(), s.state.reward.getRewardId()));
            QuestTestAssertions.equal(s.fromPlayer(player -> player.totalExperience), baseline + 20);
        }
    }

    void rosterAndProjection() {
        try (var s = scenario()) {
            s.request(CreateTeamPayload::new, true);
            UUID team = teamId();
            UUID foreignClaim = UUID.randomUUID();
            s.onPlayer(player -> {
                s.state.manager.invite(player.getUUID(), team, s.state.member.getGameProfile().name());
                s.state.manager.respondToInvitation(s.state.member, team, true);
                s.state.manager.getTeam(team).orElseThrow().getClaimedRewards(s.state.member.getUUID()).add(foreignClaim);
                s.state.logout(s.state.member);
            });
            s.refresh();
            context.runOnClient(client -> {
                var member = ClientTeamData.getTeam().members().stream().filter(profile -> profile.id().equals(s.state.member.getUUID())).findFirst().orElseThrow();
                QuestTestAssertions.equal(member.name(), s.state.member.getGameProfile().name());
                QuestTestAssertions.check(!ClientQuestData.getUser().getClaimedRewards().contains(foreignClaim));
            });
            openSettings();
            context.takeScreenshot(environment + "-team-roster");
        }
    }

    void unavailableData() {
        try (var s = scenario()) {
            s.request(CreateTeamPayload::new, true);
            s.onPlayer(player -> s.active(player).completeQuestProgress(s.state.quest));
            int experience = s.fromPlayer(player -> player.totalExperience);
            String original = s.fromPlayer(player -> {
                s.state.manager.unloadPlayer(player.getUUID());
                s.state.flush();
                try {
                    Path file = s.state.players.userPath(player.getUUID());
                    String saved = Files.readString(file);
                    Files.writeString(file, "{");
                    s.state.manager.loadPlayer(player);
                    VeryUEMod.INSTANCE.syncTeamState(player);
                    return saved;
                } catch (Exception exception) { throw new IllegalStateException(exception); }
            });
            context.waitFor(client -> !ClientQuestData.isDataReady() && ClientQuestData.getUser() == null);
            s.send(new ClaimQuestRewardPayload(s.state.quest.questId(), s.state.reward.getRewardId()));
            QuestTestAssertions.equal(s.fromPlayer(player -> player.totalExperience), experience);
            s.onPlayer(player -> {
                var team = s.state.manager.getPlayerTeam(player.getUUID()).orElseThrow();
                team.resetQuestProgress(s.state.quest);
                player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
            });
            s.send(new InteractQuestObjectivePayload(s.state.quest.questId(), s.state.objective.getObjectiveId()));
            s.onPlayer(player -> QuestTestAssertions.check(s.state.manager.getPlayerTeam(player.getUUID()).orElseThrow().getObjectiveProgress(s.state.objective) == null));
            s.onPlayer(player -> {
                try { Files.writeString(s.state.players.userPath(player.getUUID()), original); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                VeryUEMod.INSTANCE.reloadServerConfig(player.level().getServer());
                QuestTestAssertions.check(!s.state.manager.isReady(player.getUUID()));
            });
            connection.waitForClientboundPackets();
            context.runOnClient(client -> {
                QuestTestAssertions.check(ClientQuestData.getUser() == null);
                ClientTeamData.request(CreateTeamPayload::new, payload -> { throw new AssertionError("Unavailable action sent"); });
            });
            s.onPlayer(player -> {
                s.state.manager.unloadPlayer(player.getUUID());
                s.state.manager.loadPlayer(player);
            });
            s.refresh();
            context.waitFor(client -> ClientQuestData.isDataReady() && ClientQuestData.getUser() != null);
        }
    }

    void rechecksAndAttributes() {
        try (var s = scenario()) {
            var objective = new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND), 3);
            var reward = new RewardAttribute(UUID.randomUUID(), Attributes.MAX_HEALTH.unwrapKey().orElseThrow().identifier(), 4);
            Quest quest = new Quest(s.state.quest.position(), s.state.quest.display(), s.state.quest.sectionUUID(), UUID.randomUUID(), List.of(objective), List.of(reward), List.of());
            s.onPlayer(player -> {
                player.getInventory().clearContent();
                QuestData data = QuestReloadDelegate.INSTANCE.getData();
                var quests = new HashMap<>(data.quests());
                var sections = new HashMap<>(data.sections());
                quests.put(quest.questId(), quest);
                sections.put(quest.sectionUUID(), new Section(quest.sectionUUID(), "Checks", Items.BOOK, List.of(s.state.quest, quest)));
                QuestReloadDelegate.INSTANCE.reload(new QuestTestLoader(new QuestData(sections, quests)), player.level().getServer().getResourceManager(), player.registryAccess());
            });
            s.refresh();
            s.request(CreateTeamPayload::new, true);
            UUID team = teamId();
            s.onPlayer(player -> player.getInventory().add(new ItemStack(Items.DIAMOND, 3)));
            s.refresh();
            context.waitFor(client -> ClientQuestData.isQuestComplete(quest));
            s.send(new ClaimQuestRewardPayload(quest.questId(), reward.getRewardId()));
            QuestTestAssertions.equal(s.fromPlayer(player -> player.getAttributeValue(Attributes.MAX_HEALTH)), 24.0);
            s.request(id -> new ChangeTeamSettingPayload(id, team, TeamSetting.KEEP_PROGRESS, false), true);
            s.request(id -> new LeaveTeamPayload(id, team), true);
            context.runOnClient(client -> QuestTestAssertions.check(ClientQuestData.isQuestComplete(quest) && ClientQuestData.isRewardClaimed(reward)));
            QuestTestAssertions.equal(s.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 3);
            QuestTestAssertions.equal(s.fromPlayer(player -> player.getAttributeValue(Attributes.MAX_HEALTH)), 24.0);
        }
    }

    void settingsControls() {
        try (var s = scenario()) {
            s.request(CreateTeamPayload::new, true);
            UUID team = teamId();
            openSettings();
            context.runOnClient(client -> {
                EditBox name = teamPanel().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                name.setValue("Keep this draft");
            });
            s.request(id -> new RenameTeamPayload(id, team, " "), false);
            context.waitTicks(2);
            context.runOnClient(client -> QuestTestAssertions.equal(teamPanel().children().stream().filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast).findFirst().orElseThrow().getValue(), "Keep this draft"));
            scrollEnd();
            click(button("Disband Team"));
            context.waitTicks(2);
            context.runOnClient(client -> QuestTestAssertions.check(ClientTeamData.getTeam() != null && !ClientTeamData.isPending()));
            scrollEnd();
            context.takeScreenshot(environment + "-disband-confirmation");
            click(button("Confirm Disband"));
            context.waitFor(client -> ClientTeamData.getTeam() == null && !ClientTeamData.isPending());
        }
    }

    void failedRequest() {
        try (var s = scenario()) {
            s.request(CreateTeamPayload::new, true);
            UUID team = teamId();
            s.onPlayer(player -> {
                s.state.teams.failedSaves.add(team);
                s.state.expectedFailure = true;
            });
            context.runOnClient(client -> ClientTeamData.request(id -> new RenameTeamPayload(id, team, "Must fail"), ClientPlayNetworking::send));
            connection.waitForServerboundPackets();
            context.waitTicks(2);
            context.runOnClient(client -> QuestTestAssertions.check(ClientTeamData.isPending()));
            s.onPlayer(player -> {
                QuestTestAssertions.check(s.state.manager.hasFatalFailure());
                QuestTestAssertions.check(TeamFailureCapture.report(s.state.directory) != null);
                QuestTestAssertions.check(!s.state.manager.getTeam(team).orElseThrow().getName().equals("Must fail"));
            });
        }
    }

    void openSettings() {
        context.setScreen(() -> null);
        context.getInput().pressKey(VeryUEKeyMappings.OPEN_QUEST_MENU);
        context.waitTicks(2);
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitForScreen(QuestSettingsScreen.class);
        context.waitTicks(1);
    }

    TeamSettingsPanel teamPanel() {
        return Minecraft.getInstance().gui.screen().children().stream()
                .filter(TeamSettingsPanel.class::isInstance).map(TeamSettingsPanel.class::cast).findFirst().orElseThrow();
    }

    Button button(String label) {
        return context.computeOnClient(client -> teamPanel().children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow());
    }

    void scrollEnd() {
        context.runOnClient(client -> {
            TeamSettingsPanel panel = teamPanel();
            panel.setFocused((GuiEventListener) null);
            client.gui.screen().setFocused(panel);
        });
        context.getInput().pressKey(InputConstants.KEY_END);
        context.waitTicks(2);
    }

    void click(AbstractWidget widget) {
        double[] position = context.computeOnClient(client -> new double[]{
                (widget.getX() + widget.getWidth() / 2.0) * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                (widget.getY() + widget.getHeight() / 2.0) * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    void writeReport() {
        try {
            Path directory = Path.of(System.getProperty("vuequests.tests.reportDirectory"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("team-" + environment + ".json"), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(Map.of("environment", environment, "cases", results)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
