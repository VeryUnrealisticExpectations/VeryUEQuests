package dev.veryue.vuequests.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.QuestPosition;
import dev.veryue.vuequests.models.reward.RewardType;
import dev.veryue.vuequests.network.ClaimQuestRewardPayload;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import dev.veryue.vuequests.network.QuestScreenOpenedPayload;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class QuestTestScenario<T, P> {
    final GameTestHelper helper;
    final ServerPlayer player;
    final User user;
    final QuestObjective<T, P> objective;
    final Quest quest;

    QuestTestScenario(GameTestHelper helper, QuestObjective<T, P> objective) {
        this(helper, objective, List.of());
    }

    QuestTestScenario(GameTestHelper helper, QuestObjective<T, P> objective, List<RewardType<?>> rewards) {
        this.helper = helper;
        this.objective = objective;
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(x, 1, z, Blocks.IRON_BLOCK);
            }
        }
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var position = helper.absolutePos(new BlockPos(4, 2, 4));
        player.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
        user = VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseThrow();
        quest = new Quest(new QuestPosition(0, 0), new QuestDisplay("", "", "", Items.BOOK, ""),
                UUID.randomUUID(), UUID.randomUUID(), List.of(objective), rewards, List.of());
        QuestData current = QuestReloadDelegate.INSTANCE.getData();
        var quests = new HashMap<>(current.quests());
        quests.put(quest.questId(), quest);
        publish(new QuestData(current.sections(), quests));
        QuestTestCleanup.add(helper, () -> {
            player.connection.disconnect(Component.empty());
            player.level().getServer().getPlayerList().remove(player);
            QuestData loaded = QuestReloadDelegate.INSTANCE.getData();
            var remaining = new HashMap<>(loaded.quests());
            remaining.remove(quest.questId());
            publish(new QuestData(loaded.sections(), remaining));
        });
    }

    void openMenu() {
        QuestScreenOpenedPayload.INSTANCE.handle(player);
    }

    boolean interact() {
        return new InteractQuestObjectivePayload(quest.questId(), objective.getObjectiveId()).handle(player);
    }

    boolean claim(RewardType<?> reward) {
        return new ClaimQuestRewardPayload(quest.questId(), reward.getRewardId()).handle(player);
    }

    void assertPending() {
        helper.assertFalse(user.hasCompletedObjective(objective), "objective completion");
        helper.assertFalse(user.hasCompletedQuest(quest), "quest completion");
    }

    void assertProgress(P expected) {
        helper.assertTrue(user.getObjectiveProgress(objective) != null, "progress");
        helper.assertValueEqual(Optional.ofNullable(user.getObjectiveProgress(objective).getCurrentProgress()), Optional.of(expected), "progress");
        assertPending();
    }

    void assertComplete(P expected) {
        helper.assertTrue(user.getObjectiveProgress(objective) != null, "progress");
        helper.assertValueEqual(Optional.ofNullable(user.getObjectiveProgress(objective).getCurrentProgress()), Optional.of(expected), "progress");
        helper.assertTrue(user.hasCompletedObjective(objective), "objective completion");
        helper.assertTrue(user.hasCompletedQuest(quest), "quest completion");
    }

    void publish(QuestData data) {
        QuestReloadDelegate.INSTANCE.reload(new QuestTestLoader(data),
                helper.getLevel().getServer().getResourceManager(), helper.getLevel().registryAccess());
    }
}
