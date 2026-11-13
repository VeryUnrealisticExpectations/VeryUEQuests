package dev.veryue.vuequests.gametest;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.VeryUEKeyMappings;
import dev.veryue.vuequests.client.screens.LinearQuestScreen;
import dev.veryue.vuequests.client.screens.QuestScreen;
import dev.veryue.vuequests.client.widget.QuestSectionToggle;
import dev.veryue.vuequests.config.QuestView;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.objective.ObjectiveAcknowledge;
import dev.veryue.vuequests.models.objective.ObjectiveArea;
import dev.veryue.vuequests.models.objective.ObjectiveCollectItem;
import dev.veryue.vuequests.models.objective.ObjectiveExperienceLevels;
import dev.veryue.vuequests.models.objective.ObjectiveGetAdvancement;
import dev.veryue.vuequests.models.objective.ObjectiveSlayEntity;
import dev.veryue.vuequests.models.objective.ObjectiveStatistic;
import dev.veryue.vuequests.models.objective.ObjectiveSubmitItem;
import dev.veryue.vuequests.models.objective.ObjectiveTravelBiome;
import dev.veryue.vuequests.models.objective.ObjectiveTravelCoordinates;
import dev.veryue.vuequests.models.objective.ObjectiveTravelDimension;
import dev.veryue.vuequests.models.objective.ObjectiveTravelStructure;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.reward.RewardAdvancement;
import dev.veryue.vuequests.models.reward.RewardAttribute;
import dev.veryue.vuequests.models.reward.RewardCommand;
import dev.veryue.vuequests.models.reward.RewardExperience;
import dev.veryue.vuequests.models.reward.RewardExperienceLevels;
import dev.veryue.vuequests.models.reward.RewardItem;
import dev.veryue.vuequests.models.reward.RewardLootTable;
import dev.veryue.vuequests.models.reward.RewardType;
import dev.veryue.vuequests.network.QuestViewSyncPayload;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class QuestClientScenarios {
    final ClientGameTestContext context;
    final TestServerContext server;
    final TestServerConnection connection;
    final List<String> passed = new ArrayList<>();

    QuestClientScenarios(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        this.context = context;
        this.server = server;
        this.connection = connection;
    }

    void run(String environment) {
        boolean dedicated = environment.equals("dedicated");
        QuestTestAssertions.equal(server.computeOnServer(instance -> instance.isDedicatedServer()), dedicated);
        context.runOnClient(client -> {
            QuestTestAssertions.equal(client.getSingleplayerServer() == null, dedicated);
            QuestTestAssertions.equal(client.getConnection().getConnection().isMemoryConnection(), !dedicated);
        });
        collectItem();
        slayEntity();
        advancement();
        submitItem();
        experienceLevels();
        acknowledge();
        dimension();
        biome();
        coordinates();
        structure();
        statistic();
        itemReward();
        experienceReward();
        levelsReward();
        advancementReward();
        lootReward();
        commandReward();
        attributeReward();
        screens(environment);
        QuestTestAssertions.equal(passed.size(), 19);
        try {
            Path directory = Path.of(System.getProperty("vuequests.tests.reportDirectory"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(environment + ".json"), new Gson().toJson(Map.of("environment", environment, "passed", passed)));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    <T, P> QuestNetworkScenario<T, P> scenario(QuestObjective<T, P> objective) {
        return new QuestNetworkScenario<>(context, server, connection, objective, List.of());
    }

    void collectItem() {
        try (var scenario = scenario(new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND), 5))) {
            scenario.onPlayer(player -> player.getInventory().add(new ItemStack(Items.DIAMOND, 2)));
            scenario.refresh();
            scenario.assertProgress(2);
            scenario.onPlayer(player -> player.getInventory().add(new ItemStack(Items.DIAMOND, 3)));
            scenario.refresh();
            scenario.assertComplete(5);
        }
        passed.add("collect_item");
    }

    void slayEntity() {
        try (var scenario = scenario(new ObjectiveSlayEntity(UUID.randomUUID(), EntityTypes.CHICKEN, 2))) {
            Consumer<ServerPlayer> kill = player -> {
                var entity = EntityTypes.CHICKEN.create(player.level(), EntitySpawnReason.TRIGGERED);
                QuestTestAssertions.check(entity != null);
                entity.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0);
                player.level().addFreshEntity(entity);
                entity.hurtServer(player.level(), player.damageSources().playerAttack(player), 1000);
            };
            scenario.onPlayer(kill);
            scenario.assertProgress(1);
            scenario.onPlayer(kill);
            scenario.assertComplete(2);
        }
        passed.add("entity");
    }

    void advancement() {
        var id = Identifier.fromNamespaceAndPath("vuequests_tests", "objective");
        try (var scenario = scenario(new ObjectiveGetAdvancement(UUID.randomUUID(), id))) {
            scenario.onPlayer(player -> player.getAdvancements().award(player.level().getServer().getAdvancements().get(id), "first"));
            context.waitTicks(2);
            scenario.assertPending();
            scenario.onPlayer(player -> player.getAdvancements().award(player.level().getServer().getAdvancements().get(id), "second"));
            scenario.assertComplete(true);
        }
        passed.add("advancement");
    }

    void submitItem() {
        try (var scenario = scenario(new ObjectiveSubmitItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND), 5))) {
            scenario.onPlayer(player -> player.getInventory().add(new ItemStack(Items.DIAMOND, 2)));
            scenario.interact();
            scenario.assertProgress(2);
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 0);
            scenario.onPlayer(player -> player.getInventory().add(new ItemStack(Items.DIAMOND, 7)));
            scenario.interact();
            scenario.assertComplete(5);
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 4);
            scenario.interact();
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 4);
        }
        passed.add("submit_item");
    }

    void experienceLevels() {
        try (var scenario = scenario(new ObjectiveExperienceLevels(UUID.randomUUID(), 5))) {
            scenario.onPlayer(player -> player.giveExperienceLevels(2));
            scenario.assertProgress(2);
            scenario.onPlayer(player -> player.giveExperienceLevels(3));
            scenario.assertComplete(5);
        }
        passed.add("experience_levels");
    }

    void acknowledge() {
        try (var scenario = scenario(new ObjectiveAcknowledge(UUID.randomUUID()))) {
            scenario.interact();
            scenario.assertComplete(true);
            scenario.interact();
            scenario.assertComplete(true);
        }
        passed.add("acknowledge");
    }

    void dimension() {
        var origin = server.computeOnServer(instance -> connection.getServerPlayer().position());
        try (var scenario = scenario(new ObjectiveTravelDimension(UUID.randomUUID(), Level.NETHER.identifier()))) {
            scenario.onPlayer(player -> player.teleportTo(player.level().getServer().getLevel(Level.NETHER),
                    0, 100, 0, Set.of(), 0, 0, true));
            scenario.assertComplete(true);
            scenario.onPlayer(player -> player.teleportTo(player.level().getServer().getLevel(Level.OVERWORLD),
                    origin.x, origin.y, origin.z, Set.of(), 0, 0, true));
            connection.waitForClientboundPackets();
            scenario.assertComplete(true);
        }
        passed.add("travel_dimension");
    }

    void biome() {
        var pos = server.computeOnServer(instance -> connection.getServerPlayer().blockPosition().offset(16, 0, 0));
        var desert = Identifier.withDefaultNamespace("desert");
        var target = server.computeOnServer(instance -> connection.getServerLevel().getBiome(connection.getServerPlayer().blockPosition())
                .is(ResourceKey.create(Registries.BIOME, desert))) ? Identifier.withDefaultNamespace("plains") : desert;
        try (var scenario = scenario(new ObjectiveTravelBiome(UUID.randomUUID(), target))) {
            int minimumY = server.computeOnServer(instance -> connection.getServerLevel().getMinY());
            server.runCommand("fillbiome " + (pos.getX() - 8) + " " + Math.max(minimumY, pos.getY() - 8) + " " + (pos.getZ() - 8) + " "
                    + (pos.getX() + 8) + " " + (pos.getY() + 8) + " " + (pos.getZ() + 8) + " " + target);
            scenario.onPlayer(player -> player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
            scenario.assertComplete(true);
        }
        passed.add("travel_biome");
    }

    void coordinates() {
        var pos = server.computeOnServer(instance -> connection.getServerPlayer().blockPosition().offset(2, 0, 0));
        var area = new ObjectiveArea(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        try (var scenario = scenario(new ObjectiveTravelCoordinates(UUID.randomUUID(), Level.OVERWORLD.identifier(), area))) {
            scenario.onPlayer(player -> player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
            scenario.assertComplete(true);
        }
        passed.add("travel_coordinates");
    }

    void structure() {
        var id = Identifier.withDefaultNamespace("stronghold");
        try (var scenario = scenario(new ObjectiveTravelStructure(UUID.randomUUID(), id))) {
            Runnable cleanup = scenario.fromPlayer(player -> {
                var level = player.level();
                var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(ResourceKey.create(Registries.STRUCTURE, id)).value();
                var pos = player.blockPosition().offset(1, 0, 0);
                var piece = new StrongholdPieces.StartPiece(RandomSource.create(0), pos.getX(), pos.getZ());
                var box = piece.getBoundingBox();
                piece.move(pos.getX() - box.minX(), pos.getY() - box.minY(), pos.getZ() - box.minZ());
                var chunk = level.getChunkAt(pos);
                var previous = chunk.getStartForStructure(structure);
                boolean referenced = chunk.getReferencesForStructure(structure).contains(chunk.getPos().pack());
                level.structureManager().setStartForStructure(structure,
                        new StructureStart(structure, chunk.getPos(), 0, new PiecesContainer(List.of(piece))), chunk);
                level.structureManager().addReferenceForStructure(structure, chunk.getPos().pack(), chunk);
                player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                return () -> {
                    chunk.setStartForStructure(structure, previous == null ? StructureStart.INVALID_START : previous);
                    if (!referenced) {
                        chunk.getReferencesForStructure(structure).remove(chunk.getPos().pack());
                    }
                };
            });
            try {
                scenario.assertComplete(true);
            } finally {
                server.runOnServer(instance -> cleanup.run());
            }
        }
        passed.add("travel_structure");
    }

    void statistic() {
        var statistic = Stats.CUSTOM.get(Stats.JUMP);
        try (var scenario = scenario(new ObjectiveStatistic(UUID.randomUUID(), statistic, 2))) {
            scenario.onPlayer(player -> player.awardStat(statistic, 1));
            scenario.assertProgress(1);
            scenario.onPlayer(player -> player.awardStat(statistic, 1));
            scenario.assertComplete(2);
        }
        passed.add("statistic");
    }

    QuestNetworkScenario<Boolean, Boolean> rewardScenario(RewardType<?> reward) {
        var scenario = new QuestNetworkScenario<>(context, server, connection,
                new ObjectiveAcknowledge(UUID.randomUUID()), List.of(reward));
        scenario.claim(reward);
        context.runOnClient(client -> QuestTestAssertions.check(!ClientQuestData.isRewardClaimed(reward)));
        scenario.interact();
        scenario.assertComplete(true);
        scenario.claim(reward);
        scenario.assertClaimed(reward);
        return scenario;
    }

    void itemReward() {
        var reward = new RewardItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND, 70));
        try (var scenario = rewardScenario(reward)) {
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 70);
            scenario.claim(reward);
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 70);
            context.waitFor(client -> client.player.getInventory().countItem(Items.DIAMOND) == 70);
        }
        passed.add("reward_item");
    }

    void experienceReward() {
        var reward = new RewardExperience(UUID.randomUUID(), 25);
        try (var scenario = rewardScenario(reward)) {
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.totalExperience), 25);
            scenario.claim(reward);
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.totalExperience), 25);
        }
        passed.add("reward_experience");
    }

    void levelsReward() {
        var reward = new RewardExperienceLevels(UUID.randomUUID(), 5);
        try (var scenario = rewardScenario(reward)) {
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.experienceLevel), 5);
            scenario.claim(reward);
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.experienceLevel), 5);
            context.waitFor(client -> client.player.experienceLevel == 5);
        }
        passed.add("reward_experience_levels");
    }

    void advancementReward() {
        var id = Identifier.fromNamespaceAndPath("vuequests_tests", "reward");
        var reward = new RewardAdvancement(UUID.randomUUID(), id);
        try (var scenario = rewardScenario(reward)) {
            scenario.onPlayer(player -> QuestTestAssertions.check(player.getAdvancements()
                    .getOrStartProgress(player.level().getServer().getAdvancements().get(id)).isDone()));
            scenario.claim(reward);
            scenario.assertClaimed(reward);
        }
        passed.add("reward_advancement");
    }

    void lootReward() {
        var reward = new RewardLootTable(UUID.randomUUID(), Identifier.fromNamespaceAndPath("vuequests_tests", "reward"));
        try (var scenario = rewardScenario(reward)) {
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 3);
            scenario.claim(reward);
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 3);
            context.waitFor(client -> client.player.getInventory().countItem(Items.DIAMOND) == 3);
        }
        passed.add("reward_loot_table");
    }

    void commandReward() {
        var reward = new RewardCommand(UUID.randomUUID(), "give @s minecraft:diamond 4");
        try (var scenario = rewardScenario(reward)) {
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 4);
            scenario.claim(reward);
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getInventory().countItem(Items.DIAMOND)), 4);
            context.waitFor(client -> client.player.getInventory().countItem(Items.DIAMOND) == 4);
        }
        passed.add("reward_command");
    }

    void attributeReward() {
        var reward = new RewardAttribute(UUID.randomUUID(), Attributes.MAX_HEALTH.unwrapKey().orElseThrow().identifier(), 4);
        try (var scenario = rewardScenario(reward)) {
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getAttributeValue(Attributes.MAX_HEALTH)), 24.0);
            scenario.claim(reward);
            scenario.onPlayer(player -> QuestReloadDelegate.INSTANCE.getData().restoreAttributeRewards(
                    VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseThrow(), player));
            QuestTestAssertions.equal(scenario.fromPlayer(player -> player.getAttributeValue(Attributes.MAX_HEALTH)), 24.0);
            context.waitFor(client -> client.player.getAttributeValue(Attributes.MAX_HEALTH) == 24.0);
        }
        passed.add("reward_attribute");
    }

    void screens(String environment) {
        try (var scenario = scenario(new ObjectiveAcknowledge(UUID.randomUUID()))) {
            scenario.onPlayer(player -> ServerPlayNetworking.send(player, new QuestViewSyncPayload(QuestView.GRAPH)));
            context.waitFor(client -> ClientQuestData.getQuestView() == QuestView.GRAPH);
            context.getInput().pressKey(VeryUEKeyMappings.OPEN_QUEST_MENU);
            context.waitForScreen(QuestScreen.class);
            var toggle = context.computeOnClient(client -> client.gui.screen().children().stream()
                    .filter(QuestSectionToggle.class::isInstance).map(QuestSectionToggle.class::cast).findFirst().orElseThrow());
            var initial = context.computeOnClient(client -> toggle.getMessage().getString());
            clickToggle(toggle);
            context.waitFor(client -> !toggle.getMessage().getString().equals(initial));
            clickToggle(toggle);
            context.waitFor(client -> toggle.getMessage().getString().equals(initial));
            context.takeScreenshot(environment + "-graph");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(null);
            scenario.onPlayer(player -> ServerPlayNetworking.send(player, new QuestViewSyncPayload(QuestView.LIST)));
            context.waitFor(client -> ClientQuestData.getQuestView() == QuestView.LIST);
            context.getInput().pressKey(VeryUEKeyMappings.OPEN_QUEST_MENU);
            context.waitForScreen(LinearQuestScreen.class);
            context.takeScreenshot(environment + "-list");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(null);
        }
        passed.add("client_screens");
    }

    void clickToggle(QuestSectionToggle toggle) {
        var position = context.computeOnClient(client -> new double[]{
                (toggle.getX() + toggle.getWidth() / 2.0) * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                (toggle.getY() + toggle.getHeight() / 2.0) * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
}
