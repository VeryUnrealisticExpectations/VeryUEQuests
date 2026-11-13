package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.QuestPosition;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.models.reward.RewardType;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class QuestNetworkScenario<T, P> implements AutoCloseable {
    final ClientGameTestContext context;
    final TestServerContext server;
    final TestServerConnection connection;
    final QuestObjective<T, P> objective;
    final Quest quest;

    QuestNetworkScenario(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
                         QuestObjective<T, P> objective, List<RewardType<?>> rewards) {
        this.context = context;
        this.server = server;
        this.connection = connection;
        this.objective = objective;
        quest = new Quest(new QuestPosition(0, 0), new QuestDisplay("", "", "", Items.BOOK, ""),
                UUID.randomUUID(), UUID.randomUUID(), List.of(objective), rewards, List.of());
        context.setScreen(() -> null);
        onPlayer(player -> {
            player.getInventory().clearContent();
            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            player.totalExperience = 0;
            QuestData current = QuestReloadDelegate.INSTANCE.getData();
            var quests = new HashMap<>(current.quests());
            var sections = new HashMap<>(current.sections());
            quests.put(quest.questId(), quest);
            sections.put(quest.sectionUUID(), new Section(quest.sectionUUID(), "", Items.BOOK, List.of(quest)));
            publish(player, new QuestData(sections, quests));
        });
        refresh();
        context.waitFor(client -> ClientQuestData.getData().quests().containsKey(quest.questId()));
        assertPending();
    }

    void onPlayer(Consumer<ServerPlayer> action) {
        server.runOnServer(instance -> action.accept(connection.getServerPlayer()));
    }

    <R> R fromPlayer(Function<ServerPlayer, R> function) {
        return server.computeOnServer(instance -> function.apply(connection.getServerPlayer()));
    }

    void send(CustomPacketPayload payload) {
        context.runOnClient(client -> ClientPlayNetworking.send(payload));
        connection.waitForServerboundPackets();
        connection.waitForClientboundPackets();
    }

    void refresh() {
        send(QuestScreenOpenedPayload.INSTANCE);
    }

    void interact() {
        send(new InteractQuestObjectivePayload(quest.questId(), objective.getObjectiveId()));
    }

    void claim(RewardType<?> reward) {
        send(new ClaimQuestRewardPayload(quest.questId(), reward.getRewardId()));
    }

    void assertPending() {
        onPlayer(player -> {
            User user = VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseThrow();
            QuestTestAssertions.check(!user.hasCompletedObjective(objective));
            QuestTestAssertions.check(!user.hasCompletedQuest(quest));
        });
        context.runOnClient(client -> {
            User user = ClientQuestData.getUser();
            QuestTestAssertions.check(user != null && !user.hasCompletedObjective(objective) && !user.hasCompletedQuest(quest));
        });
    }

    void assertProgress(P expected) {
        context.waitFor(client -> {
            var user = ClientQuestData.getUser();
            var progress = user == null ? null : user.getObjectiveProgress(objective);
            return progress != null && Objects.equals(progress.getCurrentProgress(), expected);
        });
        onPlayer(player -> QuestTestAssertions.equal(VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseThrow()
                .getObjectiveProgress(objective).getCurrentProgress(), expected));
        assertPending();
    }

    void assertComplete(P expected) {
        context.waitFor(client -> {
            var user = ClientQuestData.getUser();
            var progress = user == null ? null : user.getObjectiveProgress(objective);
            return progress != null && Objects.equals(progress.getCurrentProgress(), expected)
                    && user.hasCompletedObjective(objective) && user.hasCompletedQuest(quest);
        });
        onPlayer(player -> {
            User user = VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseThrow();
            QuestTestAssertions.equal(user.getObjectiveProgress(objective).getCurrentProgress(), expected);
            QuestTestAssertions.check(user.hasCompletedObjective(objective) && user.hasCompletedQuest(quest));
        });
    }

    void assertClaimed(RewardType<?> reward) {
        context.waitFor(client -> ClientQuestData.isRewardClaimed(reward));
        onPlayer(player -> QuestTestAssertions.check(VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseThrow()
                .getClaimedRewards().contains(reward.getRewardId())));
    }

    @Override
    public void close() {
        context.setScreen(() -> null);
        onPlayer(player -> {
            QuestData current = QuestReloadDelegate.INSTANCE.getData();
            var quests = new HashMap<>(current.quests());
            var sections = new HashMap<>(current.sections());
            quests.remove(quest.questId());
            sections.remove(quest.sectionUUID());
            publish(player, new QuestData(sections, quests));
        });
        refresh();
    }

    void publish(ServerPlayer player, QuestData data) {
        QuestReloadDelegate.INSTANCE.reload(new QuestTestLoader(data), player.level().getServer().getResourceManager(),
                player.level().registryAccess());
    }
}
