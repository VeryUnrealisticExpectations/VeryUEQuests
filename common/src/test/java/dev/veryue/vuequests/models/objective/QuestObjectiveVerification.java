package dev.veryue.vuequests.models.objective;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.QuestPosition;
import dev.veryue.vuequests.network.InteractQuestObjectivePayload;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestObjectiveVerification {
    private static int checks;

    public static int verify(Gson json, RegistryOps<JsonElement> ops) {
        checks = 0;
        List<String> definitions = List.of(
                "{\"type\":\"vuequests:submit_item\",\"item\":{\"id\":\"minecraft:stone\"},\"count\":128}",
                "{\"type\":\"vuequests:experience_levels\",\"levels\":30}",
                "{\"type\":\"vuequests:travel_dimension\",\"dimension\":\"minecraft:the_nether\"}",
                "{\"type\":\"vuequests:travel_biome\",\"biome\":\"minecraft:plains\"}",
                "{\"type\":\"vuequests:travel_structure\",\"structure\":\"minecraft:stronghold\"}",
                "{\"type\":\"vuequests:travel_coordinates\",\"dimension\":\"minecraft:overworld\",\"min_x\":-5,\"min_y\":0,\"min_z\":-5,\"max_x\":5,\"max_y\":80,\"max_z\":5}",
                "{\"type\":\"vuequests:statistic\",\"stat_type\":\"minecraft:custom\",\"statistic\":\"minecraft:jump\",\"count\":100}",
                "{\"type\":\"vuequests:statistic\",\"stat_type\":\"minecraft:mined\",\"statistic\":\"minecraft:stone\",\"count\":50}",
                "{\"type\":\"vuequests:acknowledgement\"}");
        for (String definition : definitions) {
            QuestObjective<?, ?> objective = json.fromJson(definition, QuestObjective.class);
            JsonElement encoded = QuestObjective.CODEC.encodeStart(ops, objective).getOrThrow();
            QuestObjective<?, ?> decoded = QuestObjective.CODEC.parse(ops, encoded).getOrThrow();
            check(decoded.getObjectiveId().equals(objective.getObjectiveId()) && decoded.getType() == objective.getType(), "Objective definition round trip");
            check(objective.getType().schema() != null, "Objective authoring schema");
            JsonObject progressJson = new JsonObject();
            progressJson.addProperty("objective_id", objective.getObjectiveId().toString());
            progressJson.addProperty("type", encoded.getAsJsonObject().get("type").getAsString());
            if (objective instanceof CountedQuestObjective<?>) {
                progressJson.addProperty("current_progress", 1);
            } else {
                progressJson.addProperty("current_progress", true);
            }
            PlayerUser user = new PlayerUser(UUID.randomUUID());
            user.getQuestObjectiveProgress().put(objective.getObjectiveId(), QuestObjectiveProgress.CODEC.parse(ops, progressJson).getOrThrow());
            PlayerUser restored = PlayerUser.CODEC.parse(ops, PlayerUser.CODEC.encodeStart(ops, user).getOrThrow()).getOrThrow();
            check(decoded.getProgressFraction(restored) > 0, "Typed objective progress persists");
        }
        JsonObject invalid = json.fromJson(definitions.get(5), JsonObject.class);
        invalid.addProperty("objective_id", UUID.randomUUID().toString());
        invalid.addProperty("min_x", 6);
        check(QuestObjective.CODEC.parse(ops, invalid).error().isPresent(), "Reversed coordinate bounds are rejected");
        invalid = json.fromJson(definitions.get(6), JsonObject.class);
        invalid.addProperty("objective_id", UUID.randomUUID().toString());
        invalid.addProperty("statistic", "minecraft:missing_statistic");
        check(QuestObjective.CODEC.parse(ops, invalid).error().isPresent(), "Unknown statistics are rejected");
        verifyBounds();
        verifyAcknowledgement();
        verifySubmission();
        return checks;
    }

    private static void verifyBounds() {
        ObjectiveArea box = new ObjectiveArea(-2, -1, -3, 2, 1, 3);
        check(box.contains(new BlockPos(-2, -1, -3)) && box.contains(new BlockPos(2, 1, 3)), "Both box corners are inclusive");
        for (BlockPos outside : List.of(new BlockPos(-3, 0, 0), new BlockPos(3, 0, 0), new BlockPos(0, -2, 0),
                new BlockPos(0, 2, 0), new BlockPos(0, 0, -4), new BlockPos(0, 0, 4))) {
            check(!box.contains(outside), "Each box face excludes outside blocks");
        }
        ObjectiveArea block = new ObjectiveArea(-1, 64, -1, -1, 64, -1);
        check(block.contains(BlockPos.containing(-0.1, 64.9, -0.9)), "Negative fractional positions use Minecraft block coordinates");
    }

    private static void verifyAcknowledgement() {
        PlayerUser user = new PlayerUser(UUID.randomUUID());
        ObjectiveAcknowledge first = new ObjectiveAcknowledge(UUID.randomUUID());
        ObjectiveAcknowledge second = new ObjectiveAcknowledge(UUID.randomUUID());
        ObjectiveAcknowledge locked = new ObjectiveAcknowledge(UUID.randomUUID());
        ObjectiveCollectItem automatic = new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.STONE));
        UUID sectionId = UUID.randomUUID();
        Quest root = quest(sectionId, List.of(first, second, automatic), List.of());
        Quest dependent = quest(sectionId, List.of(locked), List.of(root.questId()));
        QuestData data = new QuestData(Map.of(), Map.of(root.questId(), root, dependent.questId(), dependent));
        check(!data.handleQuestTriggerChanges(user, QuestObjectiveTypes.ACKNOWLEDGEMENT, new ObjectiveContext(null, Map.of())), "An ordinary trigger does not acknowledge objectives");
        ObjectiveContext click = new ObjectiveContext(null, Map.of(ObjectiveContext.INTERACTION, first.getObjectiveId()));
        check(!data.handleQuestTriggerChanges(user, QuestObjectiveTypes.ACKNOWLEDGEMENT, click), "A synthetic click without a server player cannot acknowledge an objective");
        check(!user.hasCompletedObjective(first) && !user.hasCompletedObjective(second) && !user.hasCompletedObjective(locked), "Missing-player clicks leave all objectives incomplete");
        check(user.getObjectiveProgress(locked) == null, "Trigger dispatch does not update locked objectives");
        InteractQuestObjectivePayload payload = new InteractQuestObjectivePayload(root.questId(), first.getObjectiveId());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            InteractQuestObjectivePayload.STREAM_CODEC.encode(buffer, payload);
            check(InteractQuestObjectivePayload.STREAM_CODEC.decode(buffer).equals(payload), "Interaction packet preserves both target UUIDs");
        } finally {
            buffer.release();
        }
    }

    private static Quest quest(UUID sectionId, List<QuestObjective<?, ?>> objectives, List<UUID> dependencies) {
        return new Quest(new QuestPosition(0, 0), new QuestDisplay(Component.literal("Test"), Component.empty(), Component.empty(), Items.BOOK, ""),
                sectionId, UUID.randomUUID(), objectives, List.of(), dependencies);
    }

    private static void verifySubmission() {
        ObjectiveSubmitItem objective = new ObjectiveSubmitItem(UUID.randomUUID(), new ItemStack(Items.STONE), 100);
        PlayerUser user = new PlayerUser(UUID.randomUUID());
        var progress = user.incrementObjectiveProgress(objective,
                new ObjectiveContext(null, Map.of(ObjectiveContext.INTERACTION, objective.getObjectiveId())));
        check(progress.getCurrentProgress() == null && !progress.isComplete(objective), "A synthetic submission without a server player cannot make progress");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
