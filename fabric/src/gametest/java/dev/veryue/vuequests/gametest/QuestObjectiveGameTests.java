package dev.veryue.vuequests.gametest;

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
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.gametest.framework.GameTestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class QuestObjectiveGameTests {
    @GameTest(maxTicks = 100)
    public void collectItemProgressAndCompletion(GameTestHelper helper) {
        var scenario = new QuestTestScenario<>(helper,
                new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND), 5));
        scenario.player.getInventory().add(new ItemStack(Items.EMERALD, 5));
        scenario.openMenu();
        scenario.assertPending();
        scenario.player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        scenario.openMenu();
        scenario.assertProgress(2);
        scenario.player.getInventory().removeItem(scenario.player.getInventory().findSlotMatchingItem(new ItemStack(Items.DIAMOND)), 2);
        scenario.openMenu();
        scenario.assertProgress(2);
        scenario.player.getInventory().add(new ItemStack(Items.DIAMOND, 5));
        scenario.openMenu();
        scenario.assertComplete(5);
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 5, "inventory");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void slayEntityProgressAndCompletion(GameTestHelper helper) {
        var scenario = new QuestTestScenario<>(helper, new ObjectiveSlayEntity(UUID.randomUUID(), EntityTypes.CHICKEN, 2));
        var cow = helper.spawn(EntityTypes.COW, new BlockPos(2, 2, 2));
        cow.hurtServer(helper.getLevel(), scenario.player.damageSources().playerAttack(scenario.player), 1000);
        scenario.assertPending();
        var first = helper.spawn(EntityTypes.CHICKEN, new BlockPos(2, 2, 2));
        first.hurtServer(helper.getLevel(), scenario.player.damageSources().playerAttack(scenario.player), 1000);
        scenario.assertProgress(1);
        var second = helper.spawn(EntityTypes.CHICKEN, new BlockPos(2, 2, 2));
        second.hurtServer(helper.getLevel(), scenario.player.damageSources().playerAttack(scenario.player), 1000);
        scenario.assertComplete(2);
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void advancementProgressAndCompletion(GameTestHelper helper) {
        var id = Identifier.fromNamespaceAndPath("vuequests_tests", "objective");
        var scenario = new QuestTestScenario<>(helper, new ObjectiveGetAdvancement(UUID.randomUUID(), id));
        var advancement = helper.getLevel().getServer().getAdvancements().get(id);
        helper.assertTrue(advancement != null, "advancement");
        scenario.openMenu();
        scenario.assertPending();
        scenario.player.getAdvancements().award(advancement, "first");
        scenario.assertPending();
        scenario.player.getAdvancements().award(advancement, "second");
        scenario.assertComplete(true);
        scenario.player.getAdvancements().revoke(advancement, "second");
        scenario.openMenu();
        scenario.assertComplete(true);
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void submitItemProgressAndCompletion(GameTestHelper helper) {
        var scenario = new QuestTestScenario<>(helper,
                new ObjectiveSubmitItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND), 5));
        scenario.player.getInventory().add(new ItemStack(Items.EMERALD, 5));
        scenario.interact();
        scenario.assertProgress(0);
        scenario.player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        scenario.openMenu();
        scenario.assertProgress(0);
        helper.assertTrue(scenario.interact(), "submission");
        scenario.assertProgress(2);
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 0, "consumed items");
        scenario.player.getInventory().add(new ItemStack(Items.DIAMOND, 7));
        helper.assertTrue(scenario.interact(), "submission");
        scenario.assertComplete(5);
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 4, "remaining items");
        helper.assertFalse(scenario.interact(), "repeated submission");
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 4, "remaining items");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void experienceLevelsProgressAndCompletion(GameTestHelper helper) {
        var scenario = new QuestTestScenario<>(helper, new ObjectiveExperienceLevels(UUID.randomUUID(), 5));
        scenario.player.giveExperienceLevels(2);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            scenario.assertProgress(2);
            scenario.player.giveExperienceLevels(-1);
        }).thenIdle(2).thenExecute(() -> {
            scenario.assertProgress(2);
            scenario.player.giveExperienceLevels(4);
        }).thenIdle(2).thenExecute(() -> scenario.assertComplete(5)).thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void acknowledgeProgressAndCompletion(GameTestHelper helper) {
        var scenario = new QuestTestScenario<>(helper, new ObjectiveAcknowledge(UUID.randomUUID()));
        scenario.openMenu();
        scenario.assertPending();
        helper.assertFalse(new InteractQuestObjectivePayload(scenario.quest.questId(), UUID.randomUUID())
                .handle(scenario.player), "unrelated objective");
        scenario.assertPending();
        helper.assertTrue(scenario.interact(), "acknowledgement");
        scenario.assertComplete(true);
        helper.assertFalse(scenario.interact(), "repeated acknowledgement");
        helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void dimensionProgressAndCompletion(GameTestHelper helper) {
        var scenario = new QuestTestScenario<>(helper,
                new ObjectiveTravelDimension(UUID.randomUUID(), Level.NETHER.identifier()));
        scenario.openMenu();
        scenario.assertPending();
        var destination = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(destination != null, "dimension");
        scenario.player.teleportTo(destination, 0, 100, 0, Set.of(), 0, 0, true);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            scenario.assertComplete(true);
            var origin = helper.absolutePos(new BlockPos(4, 2, 4));
            scenario.player.teleportTo(helper.getLevel(), origin.getX(), origin.getY(), origin.getZ(), Set.of(), 0, 0, true);
            scenario.openMenu();
            scenario.assertComplete(true);
        }).thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void biomeProgressAndCompletion(GameTestHelper helper) {
        var initial = helper.absolutePos(new BlockPos(4, 2, 4));
        var desert = Identifier.withDefaultNamespace("desert");
        var target = helper.getLevel().getBiome(initial).is(ResourceKey.create(Registries.BIOME, desert))
                ? Identifier.withDefaultNamespace("plains") : desert;
        var scenario = new QuestTestScenario<>(helper,
                new ObjectiveTravelBiome(UUID.randomUUID(), target));
        scenario.openMenu();
        scenario.assertPending();
        var pos = helper.absolutePos(new BlockPos(0, 2, 0));
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
                helper.getLevel().getServer().createCommandSourceStack(),
                "fillbiome " + (pos.getX() - 8) + " " + Math.max(helper.getLevel().getMinY(), pos.getY() - 8) + " " + (pos.getZ() - 8) + " "
                        + (pos.getX() + 8) + " " + (pos.getY() + 8) + " " + (pos.getZ() + 8) + " " + target);
        scenario.player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.startSequence().thenIdle(2).thenExecute(() -> scenario.assertComplete(true)).thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void coordinatesProgressAndCompletion(GameTestHelper helper) {
        var target = helper.absolutePos(new BlockPos(1, 2, 1));
        var area = new ObjectiveArea(target.getX(), target.getY(), target.getZ(), target.getX(), target.getY(), target.getZ());
        var scenario = new QuestTestScenario<>(helper,
                new ObjectiveTravelCoordinates(UUID.randomUUID(), helper.getLevel().dimension().identifier(), area));
        scenario.openMenu();
        scenario.assertPending();
        scenario.player.teleportTo(target.getX() - 0.5, target.getY(), target.getZ() + 0.5);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            scenario.assertPending();
            scenario.player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        }).thenIdle(2).thenExecute(() -> {
            scenario.assertComplete(true);
            scenario.player.teleportTo(target.getX() + 2.5, target.getY(), target.getZ() + 0.5);
        }).thenIdle(2).thenExecute(() -> scenario.assertComplete(true)).thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void structureProgressAndCompletion(GameTestHelper helper) {
        var id = Identifier.withDefaultNamespace("stronghold");
        var scenario = new QuestTestScenario<>(helper, new ObjectiveTravelStructure(UUID.randomUUID(), id));
        scenario.openMenu();
        scenario.assertPending();
        var structure = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE, id)).value();
        var pos = helper.absolutePos(new BlockPos(0, 2, 0));
        var piece = new StrongholdPieces.StartPiece(RandomSource.create(0), pos.getX(), pos.getZ());
        var box = piece.getBoundingBox();
        piece.move(pos.getX() - box.minX(), pos.getY() - box.minY(), pos.getZ() - box.minZ());
        var chunk = helper.getLevel().getChunkAt(pos);
        var previous = chunk.getStartForStructure(structure);
        boolean referenced = chunk.getReferencesForStructure(structure).contains(chunk.getPos().pack());
        var start = new StructureStart(structure, chunk.getPos(), 0, new PiecesContainer(List.of(piece)));
        helper.getLevel().structureManager().setStartForStructure(structure, start, chunk);
        helper.getLevel().structureManager().addReferenceForStructure(structure, chunk.getPos().pack(), chunk);
        QuestTestCleanup.add(helper, () -> {
            chunk.setStartForStructure(structure, previous == null ? StructureStart.INVALID_START : previous);
            if (!referenced) {
                chunk.getReferencesForStructure(structure).remove(chunk.getPos().pack());
            }
        });
        scenario.player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.startSequence().thenIdle(2).thenExecute(() -> scenario.assertComplete(true)).thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void statisticProgressAndCompletion(GameTestHelper helper) {
        var statistic = Stats.CUSTOM.get(Stats.JUMP);
        var scenario = new QuestTestScenario<>(helper, new ObjectiveStatistic(UUID.randomUUID(), statistic, 2));
        scenario.player.awardStat(Stats.CUSTOM.get(Stats.DROP), 3);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            scenario.assertPending();
            scenario.player.awardStat(statistic, 1);
        }).thenIdle(2).thenExecute(() -> {
            scenario.assertProgress(1);
            scenario.player.awardStat(statistic, 1);
        }).thenIdle(2).thenExecute(() -> scenario.assertComplete(2)).thenSucceed();
    }
}
