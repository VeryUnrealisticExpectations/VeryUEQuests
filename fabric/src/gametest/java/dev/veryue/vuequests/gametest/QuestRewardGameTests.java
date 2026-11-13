package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.objective.ObjectiveAcknowledge;
import dev.veryue.vuequests.models.reward.RewardAdvancement;
import dev.veryue.vuequests.models.reward.RewardAttribute;
import dev.veryue.vuequests.models.reward.RewardCommand;
import dev.veryue.vuequests.models.reward.RewardExperience;
import dev.veryue.vuequests.models.reward.RewardExperienceLevels;
import dev.veryue.vuequests.models.reward.RewardItem;
import dev.veryue.vuequests.models.reward.RewardLootTable;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.GameTestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import java.util.List;
import java.util.UUID;

public class QuestRewardGameTests {
    @GameTest(maxTicks = 100)
    public void claimItemReward(GameTestHelper helper) {
        var reward = new RewardItem(UUID.randomUUID(), new ItemStack(Items.DIAMOND, 70));
        var scenario = completedScenario(helper, reward);
        helper.assertTrue(scenario.claim(reward), "claim");
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 70, "inventory");
        assertClaimRecordedAndRepeatedClaimRejected(scenario, reward);
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 70, "inventory");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void claimExperienceReward(GameTestHelper helper) {
        var reward = new RewardExperience(UUID.randomUUID(), 25);
        var scenario = completedScenario(helper, reward);
        int before = scenario.player.totalExperience;
        helper.assertTrue(scenario.claim(reward), "claim");
        helper.assertValueEqual(scenario.player.totalExperience, before + 25, "experience");
        assertClaimRecordedAndRepeatedClaimRejected(scenario, reward);
        helper.assertValueEqual(scenario.player.totalExperience, before + 25, "experience");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void claimExperienceLevelsReward(GameTestHelper helper) {
        var reward = new RewardExperienceLevels(UUID.randomUUID(), 5);
        var scenario = completedScenario(helper, reward);
        int before = scenario.player.experienceLevel;
        helper.assertTrue(scenario.claim(reward), "claim");
        helper.assertValueEqual(scenario.player.experienceLevel, before + 5, "levels");
        assertClaimRecordedAndRepeatedClaimRejected(scenario, reward);
        helper.assertValueEqual(scenario.player.experienceLevel, before + 5, "levels");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void claimAdvancementReward(GameTestHelper helper) {
        var id = Identifier.fromNamespaceAndPath("vuequests_tests", "reward");
        var reward = new RewardAdvancement(UUID.randomUUID(), id);
        var scenario = completedScenario(helper, reward);
        var advancement = helper.getLevel().getServer().getAdvancements().get(id);
        helper.assertTrue(advancement != null, "advancement");
        helper.assertFalse(scenario.player.getAdvancements().getOrStartProgress(advancement).isDone(), "advancement");
        helper.assertTrue(scenario.claim(reward), "claim");
        var progress = scenario.player.getAdvancements().getOrStartProgress(advancement);
        helper.assertTrue(progress.isDone(), "advancement");
        helper.assertTrue(progress.getCriterion("first").isDone(), "first criterion");
        helper.assertTrue(progress.getCriterion("second").isDone(), "second criterion");
        assertClaimRecordedAndRepeatedClaimRejected(scenario, reward);
        helper.assertTrue(progress.isDone(), "advancement");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void claimLootTableReward(GameTestHelper helper) {
        var reward = new RewardLootTable(UUID.randomUUID(), Identifier.fromNamespaceAndPath("vuequests_tests", "reward"));
        var scenario = completedScenario(helper, reward);
        helper.assertTrue(scenario.claim(reward), "claim");
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 3, "loot");
        assertClaimRecordedAndRepeatedClaimRejected(scenario, reward);
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 3, "loot");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void claimCommandReward(GameTestHelper helper) {
        var reward = new RewardCommand(UUID.randomUUID(), "give @s minecraft:diamond 4");
        var scenario = completedScenario(helper, reward);
        helper.assertTrue(scenario.claim(reward), "claim");
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 4, "command effect");
        assertClaimRecordedAndRepeatedClaimRejected(scenario, reward);
        helper.assertValueEqual(scenario.player.getInventory().countItem(Items.DIAMOND), 4, "command effect");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void claimAttributeReward(GameTestHelper helper) {
        var reward = new RewardAttribute(UUID.randomUUID(), Attributes.MAX_HEALTH.unwrapKey().orElseThrow().identifier(), 4);
        var scenario = completedScenario(helper, reward);
        double before = scenario.player.getAttributeValue(Attributes.MAX_HEALTH);
        helper.assertTrue(scenario.claim(reward), "claim");
        helper.assertValueEqual(scenario.player.getAttributeValue(Attributes.MAX_HEALTH), before + 4, "attribute");
        assertClaimRecordedAndRepeatedClaimRejected(scenario, reward);
        QuestReloadDelegate.INSTANCE.getData().restoreAttributeRewards(scenario.user, scenario.player);
        helper.assertValueEqual(scenario.player.getAttributeValue(Attributes.MAX_HEALTH), before + 4, "attribute");
        scenario.player.getAttribute(Attributes.MAX_HEALTH).removeModifier(reward.getModifierId());
        QuestReloadDelegate.INSTANCE.getData().restoreAttributeRewards(scenario.user, scenario.player);
        helper.assertValueEqual(scenario.player.getAttributeValue(Attributes.MAX_HEALTH), before + 4, "restored attribute");
        helper.succeed();
    }

    static QuestTestScenario<Boolean, Boolean> completedScenario(GameTestHelper helper, RewardType<?> reward) {
        var scenario = new QuestTestScenario<>(helper, new ObjectiveAcknowledge(UUID.randomUUID()), List.of(reward));
        helper.assertFalse(scenario.claim(reward), "incomplete quest claim");
        helper.assertFalse(scenario.user.getClaimedRewards().contains(reward.getRewardId()), "claim record");
        helper.assertTrue(scenario.interact(), "acknowledgement");
        scenario.assertComplete(true);
        return scenario;
    }

    static void assertClaimRecordedAndRepeatedClaimRejected(QuestTestScenario<?, ?> scenario, RewardType<?> reward) {
        scenario.helper.assertTrue(scenario.user.getClaimedRewards().contains(reward.getRewardId()), "claim record");
        scenario.helper.assertFalse(scenario.claim(reward), "repeated claim");
    }
}
