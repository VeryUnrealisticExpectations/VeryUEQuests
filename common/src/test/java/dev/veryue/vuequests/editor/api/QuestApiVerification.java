package dev.veryue.vuequests.editor.api;

import com.google.common.collect.ImmutableMap;
import dev.veryue.vuequests.editor.api.models.QuestApiDocument;
import dev.veryue.vuequests.editor.api.models.requests.CreateQuestRequest;
import dev.veryue.vuequests.editor.api.models.requests.CreateSectionRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestObjectivesRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestPositionRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestTextRequest;
import dev.veryue.vuequests.editor.api.models.responses.QuestResult;
import dev.veryue.vuequests.editor.api.models.responses.RegistrySearchResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.sun.net.httpserver.HttpServer;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.data.FlatFileUserLoader;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.editor.QuestEditorAssets;
import dev.veryue.vuequests.editor.QuestEditorResources;
import dev.veryue.vuequests.editor.QuestEditorService;
import dev.veryue.vuequests.editor.QuestEditorStorage;
import dev.veryue.vuequests.editor.QuestEditorStorage.Document;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.ObjectiveCollectItem;
import dev.veryue.vuequests.models.objective.ObjectiveGetAdvancement;
import dev.veryue.vuequests.models.objective.ObjectiveSlayEntity;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.QuestPosition;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.network.QuestSyncPayload;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import static dev.veryue.vuequests.editor.QuestEditorConstants.API_CONTEXTS;
import static dev.veryue.vuequests.editor.QuestEditorConstants.READ_ROUTES;
import static dev.veryue.vuequests.editor.QuestEditorConstants.WRITE_ROUTES;

public class QuestApiVerification {
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        VeryUERegistries.init();
        check(QuestApiTestObjective.TYPE.schema() != null, "Custom schema metadata");
        check(QuestApiTestObjective.LEGACY.schema() == null, "Legacy registration compatibility");
        QuestApiTextProgressObjective.init();
        VeryUERegistries.QUEST_OBJECTIVE_TYPES.freeze();
        VeryUERegistries.REWARD_TYPES.freeze();
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        Gson json = QuestApiJson.create(ops);
        verifyModels(json, ops);
        verifyObjectiveCounts(json, ops);
        verifyAdvancementObjective(json, ops);
        verifyObjectiveProgress();
        verifyUserSerialization();
        verifyQuestBackgrounds();
        verifyHttp();
        System.out.println("Quest API verification passed: " + checks + " checks.");
    }

    private static void verifyModels(Gson json, RegistryOps<JsonElement> ops) throws Exception {
        UUID sectionId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String body = """
                {"sectionId":"%s","questId":"%s","position":{"x":-80,"y":25},
                 "title":{"text":"Gather wood","color":"gold","bold":true,"extra":[{"translate":"test.title","fallback":"!"}]},
                 "description":["Collect ",{"text":"logs","italic":true}],"tooltip":"Plain tooltip",
                 "objectives":[{"type":"vuequests:collect_item","item":{"id":"minecraft:oak_log","count":5,
                     "components":{"minecraft:custom_name":"{\\"text\\":\\"Quest log\\"}"}}},
                     {"type":"test:custom","custom_value":42}],
                 "rewards":[{"type":"vuequests:experience","experience":10}]} 
                """.formatted(sectionId, firstId);
        CreateQuestRequest request = json.fromJson(body, CreateQuestRequest.class);
        check(request.objectives().size() == 2, "Registered objective dispatch");
        check(request.title().getStyle().isBold(), "Native styled title");
        check(request.description().getString().equals("Collect logs"), "Native component array");
        ObjectiveCollectItem legacyCollect = (ObjectiveCollectItem) request.objectives().getFirst();
        check(legacyCollect.getCount() == 5 && legacyCollect.getIconStack().getCount() == 1,
                "Legacy item count becomes the objective target while its icon remains a single item");
        check(request.objectives().get(0).getIconStack().getHoverName().getString().equals("Quest log"), "Native item component preserved");
        check(request.objectives().get(1).getValue().equals(42), "Custom codec fields preserved");
        check(request.objectives().get(0).getObjectiveId() != null && request.rewards().get(0).getRewardId() != null, "Generated child UUIDs");
        QuestApiDocument document = new QuestApiDocument(List.of(), ops, ignored -> false);
        QuestApiService.createSection(document, new CreateSectionRequest(sectionId, Component.literal("Section"), "minecraft:book", null));
        QuestResult first = QuestApiService.createQuest(document, request);
        check(first.questId().equals(firstId) && first.position().x() == -80, "Quest creation result and position");
        expect(QuestApiException.class, () -> QuestApiService.createQuest(document, request), "Duplicate creation rejected");
        CreateQuestRequest second = json.fromJson("""
                {"sectionId":"%s","questId":"%s","dependencies":["%s"],"objectives":[{"type":"vuequests:entity","entity":"minecraft:zombie","count":150}],
                 "rewards":[{"type":"vuequests:item","item":{"id":"minecraft:diamond"}}]}
                """.formatted(sectionId, secondId, firstId), CreateQuestRequest.class);
        QuestApiService.createQuest(document, second);
        QuestEditorService.validate(document.encode(), ops, ignored -> false);
        check(true, "Cross-quest dependency validated");
        JsonObject questJson = json.toJsonTree(document.quest(firstId)).getAsJsonObject();
        JsonObject display = questJson.getAsJsonObject("display");
        check(!display.has("background_sprite"), "Default background is omitted in serialized quests");
        Quest loaded = json.fromJson(questJson, Quest.class);
        check(loaded.objectives().get(1).getValue().equals(42), "Native quest response round trip");
        check(loaded.display().backgroundSprite().equals(QuestDisplay.DEFAULT_BACKGROUND_SPRITE), "Legacy quest display uses the square background");
        String customBackground = "test:quest_backgrounds/shapes/diamond";
        display.addProperty("background_sprite", customBackground);
        Quest custom = json.fromJson(questJson, Quest.class);
        check(custom.display().backgroundSprite().equals(ResourceLocation.parse(customBackground)), "Custom background decodes as a sprite ID");
        document.update(custom);
        Quest old = document.quest(firstId);
        document.update(new Quest(old.position(), old.display(), old.sectionUUID(), old.questId(), old.objectives(), old.rewards(), List.of(secondId)));
        expect(IllegalArgumentException.class, () -> QuestEditorService.validate(document.encode(), ops, ignored -> false), "Cycle rejected before persistence");
        document.update(old);
        Quest other = document.quest(secondId);
        document.update(new Quest(other.position(), other.display(), other.sectionUUID(), other.questId(), old.objectives(), other.rewards(), other.dependencies()));
        expect(IllegalArgumentException.class, () -> QuestEditorService.validate(document.encode(), ops, ignored -> false), "Duplicate objective UUID rejected");
        document.update(other);
        verifyProgression(old, other);
        expect(JsonParseException.class, () -> json.fromJson(body.replace("minecraft:oak_log", "test:absent"), CreateQuestRequest.class), "Missing registry entry rejected");
        expect(JsonParseException.class, () -> json.fromJson(body.replace("\"count\":5", "\"count\":0"), CreateQuestRequest.class), "Invalid stack count is not defaulted to one");
        expect(JsonParseException.class, () -> json.fromJson(body.replace("\"count\":5", "\"count\":1.5"), CreateQuestRequest.class), "Fractional stack count rejected");
        expect(JsonParseException.class, () -> json.fromJson(body.replace("\"x\":-80", "\"x\":1.5"), CreateQuestRequest.class), "Fractional position rejected");
        expect(JsonParseException.class, () -> json.fromJson("{\"questId\":\"1-1-1-1-1\"}", ModifyQuestTextRequest.class), "Noncanonical UUID rejected");
        expect(JsonParseException.class, () -> json.fromJson("{\"mode\":\"typo\"}", ModifyQuestObjectivesRequest.class), "Invalid list mode rejected");
        expect(IllegalArgumentException.class, () -> json.fromJson("{\"position\":{\"x\":1}}", ModifyQuestPositionRequest.class).position().toPosition(), "Incomplete position rejected");

        JsonObject unavailable = JsonParser.parseString("""
                {"section_id":"30000000-0000-4000-8000-000000000001","modLoaded":"missing_mod","custom":true}
                """).getAsJsonObject();
        QuestApiDocument preserved = new QuestApiDocument(List.of(unavailable), ops, ignored -> false);
        QuestApiService.createSection(preserved, new CreateSectionRequest(null, Component.literal("New"), null, null));
        check(preserved.encode().contains(unavailable), "Unavailable mod sections preserved without decoding");
        expect(QuestApiException.class, () -> preserved.section(UUID.fromString("30000000-0000-4000-8000-000000000001")), "Unavailable section cannot be edited");

        QuestApiDocument forward = new QuestApiDocument(List.of(), ops, ignored -> false);
        QuestApiService.createSection(forward, new CreateSectionRequest(sectionId, Component.literal("Forward"), null, null));
        QuestApiService.createQuest(forward, second);
        QuestApiService.createQuest(forward, request);
        QuestEditorService.validate(forward.encode(), ops, ignored -> false);
        check(true, "Batch forward dependency references validated after all creations");
        RegistrySearchResponse search = QuestApiService.search(Map.of("registry", "minecraft:item", "namespace", "MINECRAFT", "path", "oak", "limit", "2"));
        check(search.total() > 2 && search.entries().size() == 2, "Registry filtering and pagination");
        check(search.entries().stream().allMatch(entry -> entry.namespace().equals("minecraft") && entry.path().contains("oak")), "Namespace and path are separate AND filters");
        expect(IllegalArgumentException.class, () -> QuestApiService.search(Map.of("registry", "minecraft:item", "limit", "501")), "Search bound enforced");

        Path root = Files.createTempDirectory(Path.of("build"), "quest-api-");
        QuestEditorStorage storage = new QuestEditorStorage(root);
        storage.save(new Document(List.of(), Map.of(), Map.of(), Set.of(), "initial"), document.encode());
        Path saved = root.resolve(QuestEditorStorage.PACK_NAME).resolve("data/vuequests/vuequests/sections/editor_" + sectionId + ".json");
        Section savedSection = json.fromJson(Files.readString(saved), Section.class);
        check(savedSection.questList().size() == 2 && savedSection.questList().get(0).objectives().get(1).getValue().equals(42), "Typed changes persist as valid datapack definitions");
        check(savedSection.questList().getFirst().display().backgroundSprite().equals(custom.display().backgroundSprite()), "Custom background survives datapack persistence");
    }

    private static void verifyObjectiveCounts(Gson json, RegistryOps<JsonElement> ops) {
        ObjectiveCollectItem collect = (ObjectiveCollectItem) json.fromJson("""
                {"type":"vuequests:collect_item","count":128,"item":{"id":"minecraft:oak_log","count":4}}
                """, QuestObjective.class);
        check(collect.getCount() == 128 && collect.getStack().getCount() == 1,
                "An explicit Collect Item count overrides the legacy stack count");
        var saved = QuestObjective.CODEC.encodeStart(ops, collect).getOrThrow().getAsJsonObject();
        check(saved.get("count").getAsInt() == 128 && !saved.getAsJsonObject("item").has("count"),
                "Collect Item serializes its target separately from the stack");
        ObjectiveSlayEntity legacySlay = (ObjectiveSlayEntity) json.fromJson("""
                {"type":"vuequests:entity","entity":"minecraft:zombie"}
                """, QuestObjective.class);
        check(legacySlay.getCount() == 1, "A legacy Slay Entity objective defaults to one kill");
        ObjectiveSlayEntity slay = (ObjectiveSlayEntity) json.fromJson("""
                {"type":"vuequests:entity","entity":"minecraft:armor_stand","count":130}
                """, QuestObjective.class);
        var savedSlay = QuestObjective.CODEC.encodeStart(ops, slay).getOrThrow().getAsJsonObject();
        check(savedSlay.get("count").getAsInt() == 130 && ((ObjectiveSlayEntity)
                QuestObjective.CODEC.parse(ops, savedSlay).getOrThrow()).getCount() == 130,
                "Slay Entity round-trips a target above 99");
        for (String invalid : List.of("0", "-1", "\"100\"")) {
            JsonObject invalidCollect = saved.deepCopy();
            invalidCollect.add("count", JsonParser.parseString(invalid));
            check(QuestObjective.CODEC.parse(ops, invalidCollect).error().isPresent(),
                    "Objective count rejects " + invalid);
        }
        savedSlay.addProperty("count", Integer.MAX_VALUE);
        check(((ObjectiveSlayEntity) QuestObjective.CODEC.parse(ops, savedSlay).getOrThrow()).getCount() == Integer.MAX_VALUE,
                "Objective counts support the full positive int range");
        check(QuestObjectiveTypes.COLLECT_ITEM.schema().properties.get("count").maximum == Integer.MAX_VALUE
                        && QuestObjectiveTypes.ENTITY.schema().properties.get("count").maximum == Integer.MAX_VALUE,
                "Authoring discovery publishes the objective count range for both types");

        PlayerUser player = new PlayerUser(UUID.randomUUID());
        Quest quest = new Quest(new QuestPosition(0, 0), new QuestDisplay("Slay", "", "", Items.IRON_SWORD, ""),
                UUID.randomUUID(), UUID.randomUUID(), List.of(slay, collect), List.of(), List.of());
        QuestData data = new QuestData(Map.of(), Map.of(quest.questId(), quest));
        player.completeObjective(collect);
        var death = ObjectiveContext.createContext(null)
                .withParameter(ObjectiveContext.ENTITY, new ArmorStand(EntityType.ARMOR_STAND, null)).build();
        check(player.incrementObjectiveProgress(legacySlay, death).getCurrentProgress() == null,
                "A death of another entity type gives no slay progress");
        for (int i = 0; i < 129; i++) {
            data.handleQuestTrigger(player, QuestObjectiveTypes.ENTITY, death);
        }
        check(!player.hasCompletedObjective(slay) && slay.getProgressText(player).getString().equals("129/130"),
                "Kill triggers count above 99 and report partial progress");
        data.handleQuestTrigger(player, QuestObjectiveTypes.ENTITY, death);
        check(player.hasCompletedObjective(slay) && player.hasCompletedQuest(quest),
                "The target kill completes the objective and the mixed quest regardless of objective order");
        data.handleQuestTrigger(player, QuestObjectiveTypes.ENTITY, death);
        check(player.getObjectiveProgress(slay).getCurrentProgress() == 130, "Kills stop counting at the objective target");
    }

    private static void verifyAdvancementObjective(Gson json, RegistryOps<JsonElement> ops) {
        ObjectiveGetAdvancement objective = (ObjectiveGetAdvancement) json.fromJson("""
                {"type":"vuequests:advancement","advancement":"minecraft:story/mine_stone"}
                """, QuestObjective.class);
        ResourceLocation target = ResourceLocation.parse("minecraft:story/mine_stone");
        var definition = QuestObjective.CODEC.encodeStart(ops, objective).getOrThrow();
        check(QuestObjective.CODEC.parse(ops, definition).getOrThrow() instanceof ObjectiveGetAdvancement restored
                        && restored.getValue().equals(target) && restored.getObjectiveId().equals(objective.getObjectiveId()),
                "Advancement objectives round-trip their target ID and generated objective UUID");
        check(QuestObjectiveTypes.ADVANCEMENT.schema().required.contains("advancement"),
                "Authoring discovery exposes the advancement field");
        check(objective.getActionText().getContents() instanceof TranslatableContents action
                        && action.getKey().equals("screen.vuequests.objective.advancement")
                        && action.getArgs()[0].equals(target.toString()),
                "The advancement tooltip names the configured target");

        Quest quest = new Quest(new QuestPosition(0, 0), new QuestDisplay("Advancement", "", "", Items.BOOK, ""),
                UUID.randomUUID(), UUID.randomUUID(), List.of(objective), List.of(), List.of());
        QuestData data = new QuestData(Map.of(), Map.of(quest.questId(), quest));
        PlayerUser player = new PlayerUser(UUID.randomUUID());
        Component pending = objective.getProgressText(null);
        check(pending.getContents() instanceof TranslatableContents pendingText
                        && pendingText.getKey().equals("screen.vuequests.progress.pending")
                        && TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(pending.getStyle().getColor()),
                "An advancement without progress shows the pending status");

        ObjectiveContext other = ObjectiveContext.createContext(null)
                .withParameter(ObjectiveContext.ADVANCEMENT, ResourceLocation.parse("minecraft:story/root")).build();
        data.handleQuestTrigger(player, QuestObjectiveTypes.ADVANCEMENT, other);
        var progress = player.getObjectiveProgress(objective);
        check(progress != null && !progress.isComplete(objective) && !player.hasCompletedQuest(quest),
                "Earning an unrelated advancement leaves the quest incomplete");
        progress.setCurrentProgress(false);
        check(!progress.isComplete(objective) && objective.getProgressText(player).equals(pending),
                "False progress uses the same pending status as absent progress");

        ObjectiveContext earned = ObjectiveContext.createContext(null)
                .withParameter(ObjectiveContext.ADVANCEMENT, target).build();
        data.handleQuestTrigger(player, QuestObjectiveTypes.ADVANCEMENT, earned);
        check(Boolean.TRUE.equals(progress.getCurrentProgress()) && player.hasCompletedObjective(objective)
                        && player.hasCompletedQuest(quest), "Earning the configured advancement completes its objective and quest");
        Component done = objective.getProgressText(player);
        check(done.getContents() instanceof TranslatableContents doneText
                        && doneText.getKey().equals("screen.vuequests.progress.done")
                        && TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(done.getStyle().getColor()),
                "Boolean progress shows a green done status");
        PlayerUser restored = PlayerUser.CODEC.parse(ops, PlayerUser.CODEC.encodeStart(ops, player).getOrThrow()).getOrThrow();
        check(Boolean.TRUE.equals(restored.getObjectiveProgress(objective).getCurrentProgress())
                        && objective.getProgressText(restored).equals(done),
                "Boolean progress and its display survive the player snapshot codec");
    }

    private static void verifyObjectiveProgress() {
        PlayerUser player = new PlayerUser(UUID.randomUUID());
        ObjectiveContext context = new ObjectiveContext(null, Map.of());
        ObjectiveCollectItem collect = new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.OAK_LOG), 128);
        QuestObjectiveProgress<ItemStack, Integer> collected = player.incrementObjectiveProgress(collect, context);
        check(!collected.isComplete(collect) && collect.getProgressText(player).getString().equals("0/128"),
                "An objective without progress shows zero of its target");
        collected.setCurrentProgress(127);
        check(!collected.isComplete(collect), "Progress below the item target is incomplete");
        collected.setCurrentProgress(128);
        check(collected.isComplete(collect), "Progress at the item target completes the objective");
        collected.setCurrentProgress(129);
        check(collected.isComplete(collect) && collect.getProgressText(player).getString().equals("128/128"),
                "Progress above the item target displays the completed fraction");
        check(player.incrementObjectiveProgress(collect, context) == collected, "Triggers reuse typed progress for the same objective");
        ObjectiveCollectItem changed = new ObjectiveCollectItem(collect.getObjectiveId(), new ItemStack(Items.OAK_LOG), 150);
        check(!collected.isComplete(changed), "Completion uses the current objective definition");
        ObjectiveCollectItem other = new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.OAK_LOG, 3));
        expect(IllegalArgumentException.class, () -> collected.isComplete(other), "Completion rejects a different objective UUID");
        expect(IllegalArgumentException.class, () -> collected.increment(player, other, context), "Increments reject a different objective UUID");

        ObjectiveSlayEntity slay = new ObjectiveSlayEntity(UUID.randomUUID(), EntityType.ZOMBIE);
        QuestObjectiveProgress<EntityType<?>, Integer> slain = player.incrementObjectiveProgress(slay, context);
        check(!slain.isComplete(slay), "An entity objective without progress is incomplete");
        slain.setCurrentProgress(0);
        check(!slain.isComplete(slay), "Zero entity progress is incomplete");
        slain.setCurrentProgress(1);
        check(slain.isComplete(slay), "One entity completes the entity objective");

        QuestApiTextProgressObjective text = new QuestApiTextProgressObjective(UUID.randomUUID(), "done");
        var textProgress = player.incrementObjectiveProgress(text, context);
        check(text.getProgressText(player).getContents() instanceof TranslatableContents pending
                        && pending.getKey().equals("screen.vuequests.progress.pending"),
                "A non-counted objective can fall back to the default status");
        textProgress.setCurrentProgress("Searching");
        Component partial = text.getProgressText(player);
        check(partial.getString().equals("Searching")
                        && TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(partial.getStyle().getColor()),
                "A custom formatter receives its typed value and shares incomplete coloring");
        textProgress.setCurrentProgress("done");
        Component done = text.getProgressText(player);
        check(done.getContents() instanceof TranslatableContents completed
                        && completed.getKey().equals("screen.vuequests.progress.done")
                        && TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(done.getStyle().getColor()),
                "A custom formatter can fall back to the default completed status");
    }

    private static void verifyUserSerialization() throws Exception {
        PlayerUser player = new PlayerUser(UUID.randomUUID(), Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()));
        ObjectiveContext context = new ObjectiveContext(null, Map.of());
        ObjectiveCollectItem collect = new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.OAK_LOG, 5));
        ObjectiveCollectItem zero = new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.OAK_LOG, 5));
        ObjectiveSlayEntity slay = new ObjectiveSlayEntity(UUID.randomUUID(), EntityType.ZOMBIE);
        QuestApiTextProgressObjective text = new QuestApiTextProgressObjective(UUID.randomUUID(), "done");
        player.incrementObjectiveProgress(collect, context).setCurrentProgress(2);
        player.incrementObjectiveProgress(zero, context).setCurrentProgress(0);
        player.incrementObjectiveProgress(slay, context);
        player.incrementObjectiveProgress(text, context).setCurrentProgress("pending");

        JsonObject encoded = PlayerUser.CODEC.encodeStart(JsonOps.INSTANCE, player).getOrThrow().getAsJsonObject();
        JsonObject savedProgress = encoded.getAsJsonObject("quest_objective_progress");
        check(savedProgress.getAsJsonObject(collect.getObjectiveId().toString()).get("type").getAsString().equals("vuequests:collect_item"),
                "Saved objective progress identifies its registered type");
        PlayerUser restored = PlayerUser.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        check(restored.getUuid().equals(player.getUuid()) && restored.getCompletedQuests().equals(player.getCompletedQuests())
                        && restored.getCompletedObjectives().equals(player.getCompletedObjectives()) && restored.getClaimedRewards().equals(player.getClaimedRewards()),
                "Adding objective serialization preserves user identity, completions, and claims");
        check(restored.incrementObjectiveProgress(collect, context).getCurrentProgress().equals(2), "Integer objective progress survives serialization");
        check(restored.incrementObjectiveProgress(zero, context).getCurrentProgress().equals(0), "Zero progress is preserved independently of absent progress");
        check(restored.incrementObjectiveProgress(slay, context).getCurrentProgress() == null, "Absent objective progress survives serialization");
        check(restored.incrementObjectiveProgress(text, context).getCurrentProgress().equals("pending"), "A custom string progress codec survives serialization");
        ObjectiveCollectItem fresh = new ObjectiveCollectItem(UUID.randomUUID(), new ItemStack(Items.OAK_LOG, 5));
        restored.incrementObjectiveProgress(fresh, context);
        check(restored.getQuestObjectiveProgress().size() == 5, "Loaded progress maps accept newly encountered objectives");

        JsonObject legacy = encoded.deepCopy();
        legacy.remove("quest_objective_progress");
        check(PlayerUser.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().getQuestObjectiveProgress().isEmpty(),
                "User saves without objective progress remain supported");
        JsonObject invalidValue = encoded.deepCopy();
        invalidValue.getAsJsonObject("quest_objective_progress").getAsJsonObject(collect.getObjectiveId().toString()).addProperty("current_progress", "invalid");
        check(PlayerUser.CODEC.parse(JsonOps.INSTANCE, invalidValue).error().isPresent(), "Invalid typed progress is rejected instead of discarded");
        JsonObject invalidType = encoded.deepCopy();
        invalidType.getAsJsonObject("quest_objective_progress").getAsJsonObject(collect.getObjectiveId().toString()).addProperty("type", "test:missing");
        check(PlayerUser.CODEC.parse(JsonOps.INSTANCE, invalidType).error().isPresent(), "Unknown objective progress types are rejected");
        JsonObject invalidId = encoded.deepCopy();
        JsonObject entries = invalidId.getAsJsonObject("quest_objective_progress");
        entries.add(UUID.randomUUID().toString(), entries.remove(collect.getObjectiveId().toString()));
        check(PlayerUser.CODEC.parse(JsonOps.INSTANCE, invalidId).error().isPresent(), "Progress map UUIDs must match the stored objective UUIDs");
        ObjectiveSlayEntity changedType = new ObjectiveSlayEntity(collect.getObjectiveId(), EntityType.ZOMBIE);
        expect(IllegalArgumentException.class, () -> restored.incrementObjectiveProgress(changedType, context),
                "Loaded progress rejects an objective whose registered type has changed");
        QuestObjectiveProgress<Integer, Integer> unsupported = new QuestObjectiveProgress<>(UUID.randomUUID(), QuestApiTestObjective.LEGACY, 1);
        check(QuestObjectiveProgress.CODEC.encodeStart(JsonOps.INSTANCE, unsupported).error().isPresent(),
                "Legacy registrations require a progress codec before saving a populated progress value");

        FlatFileUserLoader loader = new FlatFileUserLoader(Files.createTempDirectory(Path.of("build"), "user-progress-"));
        check(loader.saveUser(player), "Player file storage writes typed objective progress");
        User loaded = loader.loadUser(player.getUuid());
        check(loaded.incrementObjectiveProgress(collect, context).getCurrentProgress().equals(2)
                        && loaded.incrementObjectiveProgress(text, context).getCurrentProgress().equals("pending"),
                "Player file storage restores different progress types");
    }

    private static void verifyProgression(Quest first, Quest second) {
        UUID firstId = first.questId();
        UUID secondId = second.questId();
        UUID thirdId = UUID.randomUUID();
        Quest third = new Quest(first.position(), first.display(), first.sectionUUID(), thirdId,
                List.of(), List.of(), List.of(firstId, secondId));
        verifyClientProgress(first, second, third);
        ImmutableMap<UUID, Section> sections = ImmutableMap.of();
        ImmutableMap<UUID, Quest> questIndex = ImmutableMap.of(firstId, first, secondId, second, thirdId, third);
        QuestData quests = new QuestData(sections, questIndex);
        check(quests.sections() == sections && quests.quests() == questIndex, "Quest data retains the supplied immutable maps directly");
        PlayerUser player = new PlayerUser(UUID.randomUUID());
        PlayerUser otherPlayer = new PlayerUser(UUID.randomUUID());
        ObjectiveContext context = new ObjectiveContext(null, Map.of());
        var collectObjective = first.objectives().getFirst();
        var entityObjective = second.objectives().getFirst();
        check(quests.accessibleQuests(player).equals(List.of(first)), "Root quests are accessible without progress");
        check(quests.accessibleQuests(player, entityObjective.getType()).isEmpty(), "Typed lookup excludes locked quests");
        check(quests.handleQuestTrigger(player, collectObjective.getType(), context).equals(List.of(collectObjective)),
                "Trigger selects only the matching objective from a mixed quest");
        check(quests.handleQuestTrigger(player, QuestApiTestObjective.TYPE, context).equals(List.of(first.objectives().get(1))),
                "Trigger dispatch preserves the types of a custom objective in a mixed quest");
        check(quests.handleQuestTrigger(player, QuestApiTestObjective.LEGACY, context).isEmpty(), "An unused trigger type has no matches");
        check(quests.prerequisitesMet(firstId, player), "Root quest has no prerequisites");
        check(!quests.prerequisitesMet(secondId, player), "Unmet prerequisite blocks quest");
        check(quests.availableDependents(firstId, player).isEmpty(), "Incomplete quest does not unlock dependents");
        player.grantQuestCompletion(firstId);
        check(quests.accessibleQuests(player).equals(List.of(first, second)), "A dependent stays locked until all prerequisites are met");
        check(quests.accessibleQuests(player, entityObjective.getType()).equals(List.of(second)), "Typed lookup returns the unlocked matching quest");
        check(quests.availableDependents(firstId, player).equals(List.of(secondId)), "Only dependents with all prerequisites met unlock");
        check(!quests.prerequisitesMet(secondId, otherPlayer), "Players sharing the graph have independent progress");
        player.grantQuestCompletion(secondId);
        player.grantQuestCompletion(UUID.randomUUID());
        List<Quest> accessible = quests.accessibleQuests(player);
        check(accessible.size() == 3 && accessible.containsAll(List.of(first, second, third)),
                "Multiple completed prerequisites unlock a quest once, even with a stale completion ID");
        check(quests.availableDependents(secondId, player).equals(List.of(thirdId)), "Final prerequisite unlocks a dependent");
        check(quests.availableDependents(firstId, player).equals(List.of(thirdId)), "Completed dependents are excluded");
        expect(IllegalArgumentException.class, () -> new QuestData(ImmutableMap.of(), ImmutableMap.of(secondId, second)), "Snapshot rejects missing prerequisites");
    }

    private static void verifyClientProgress(Quest first, Quest second, Quest third) {
        Section section = new Section(first.sectionUUID(), Component.literal("Progress"), Items.BOOK, List.of(first, second, third));
        PlayerUser player = new PlayerUser(UUID.randomUUID());
        ObjectiveContext context = new ObjectiveContext(null, Map.of());
        ObjectiveCollectItem objective = (ObjectiveCollectItem) first.objectives().getFirst();
        UUID rewardId = UUID.randomUUID();
        ClientQuestData.update(List.of(section), player);
        try {
            QuestData definitions = ClientQuestData.getData();
            check(definitions.sections().get(section.sectionUUID()) == section && definitions.quests().get(first.questId()) == first,
                    "Client indexes reuse the received section and quest definitions");
            expect(UnsupportedOperationException.class, () -> definitions.sections().remove(section.sectionUUID()),
                    "Published section indexes cannot be changed through their views");
            expect(UnsupportedOperationException.class, () -> definitions.quests().remove(first.questId()),
                    "Published quest indexes cannot be changed through their views");
            check(ClientQuestData.prerequisitesMet(first), "Client root quests are unlocked without progress");
            check(!ClientQuestData.prerequisitesMet(second), "Client quests are locked while a prerequisite is incomplete");
            player.grantQuestCompletion(first.questId());
            player.completeObjective(objective);
            player.getClaimedRewards().add(rewardId);
            player.incrementObjectiveProgress(objective, context).setCurrentProgress(2);
            QuestSyncPayload payload = new QuestSyncPayload(List.of(section), player);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            QuestSyncPayload decoded;
            try {
                QuestSyncPayload.STREAM_CODEC.encode(buffer, payload);
                decoded = QuestSyncPayload.STREAM_CODEC.decode(buffer);
            } finally {
                buffer.release();
            }
            check(decoded.user().getUuid().equals(player.getUuid()) && decoded.user().getCompletedQuests().equals(player.getCompletedQuests()),
                    "Quest sync preserves player identity and completed quests");
            ClientQuestData.update(decoded.sections(), decoded.user());
            PlayerUser clientUser = ClientQuestData.getUser();
            check(clientUser != null && clientUser.hasCompletedObjective(objective) && clientUser.getClaimedRewards().contains(rewardId),
                    "Client user includes completed objectives and claimed rewards");
            check(clientUser != null && clientUser.getQuestObjectiveProgress().get(objective.getObjectiveId()).getCurrentProgress().equals(2),
                    "Client user includes typed objective progress");
            player.incrementObjectiveProgress(objective, context).setCurrentProgress(3);
            check(clientUser != null && clientUser.getQuestObjectiveProgress().get(objective.getObjectiveId()).getCurrentProgress().equals(2),
                    "Server progress changes do not alter the decoded client user");
            check(ClientQuestData.getData().quests().size() == 3, "Quest sync preserves quest definitions alongside progress");
            Quest syncedSecond = ClientQuestData.getData().quests().get(second.questId());
            check(((ObjectiveSlayEntity) syncedSecond.objectives().getFirst()).getCount() == 150,
                    "Quest sync preserves objective counts above 99");
            check(ClientQuestData.prerequisitesMet(second), "Synced completion unlocks a dependent quest");
            check(!ClientQuestData.prerequisitesMet(third), "Client quests stay locked until every prerequisite is complete");
            player.grantQuestCompletion(second.questId());
            ClientQuestData.update(decoded.sections(), player);
            check(ClientQuestData.prerequisitesMet(third), "Completing every prerequisite unlocks the client quest");
            ClientQuestData.update(decoded.sections(), new PlayerUser(player.getUuid()));
            check(!ClientQuestData.prerequisitesMet(second), "A new progress snapshot replaces earlier completions");

            RegistryFriendlyByteBuf progressBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            try {
                QuestSyncPayload.STREAM_CODEC.encode(progressBuffer, new QuestSyncPayload(List.of(), player));
                QuestSyncPayload progressOnly = QuestSyncPayload.STREAM_CODEC.decode(progressBuffer);
                check(progressOnly.sections().isEmpty() && progressOnly.user().getCompletedQuests().equals(player.getCompletedQuests())
                                && progressOnly.user().getQuestObjectiveProgress().get(objective.getObjectiveId()).getCurrentProgress().equals(3),
                        "Quest sync without definitions preserves player completions and typed progress");
                ClientQuestData.update(progressOnly.sections(), progressOnly.user());
                check(ClientQuestData.getData().quests().isEmpty() && ClientQuestData.getData().sections().isEmpty()
                                && ClientQuestData.getUser() == progressOnly.user(),
                        "An empty remote definition snapshot clears earlier quests while retaining player progress");
            } finally {
                progressBuffer.release();
            }
        } finally {
            ClientQuestData.clear();
        }
        check(ClientQuestData.getData().quests().isEmpty() && ClientQuestData.getUser() == null && !ClientQuestData.prerequisitesMet(second),
                "Disconnect clears both client quest definitions and user state");
    }

    private static void verifyQuestBackgrounds() throws Exception {
        Path assets = Path.of("src/main/resources").toAbsolutePath();
        Path extra = Files.createTempDirectory(Path.of("build"), "quest-backgrounds-");
        String textureDirectory = QuestEditorAssets.QUEST_BACKGROUND_TEXTURE_DIRECTORY;
        String squarePath = "assets/vuequests/" + textureDirectory + "/background_icon_square.png";
        String diamondPath = "assets/test/" + textureDirectory + "/shapes/diamond.png";
        Files.createDirectories(extra.resolve(diamondPath).getParent());
        Files.createDirectories(extra.resolve(squarePath).getParent());
        Files.copy(assets.resolve(squarePath), extra.resolve(diamondPath));
        Files.copy(assets.resolve(squarePath + ".mcmeta"), extra.resolve(diamondPath + ".mcmeta"));
        Files.copy(assets.resolve(squarePath), extra.resolve(squarePath));
        QuestEditorAssets.setRoots(List.of(assets));
        try {
            var bundled = QuestEditorAssets.questBackgrounds();
            for (String shape : List.of("circle", "four_point_star", "rounded_square")) {
                String spriteName = "background_icon_" + shape;
                var spriteId = ResourceLocation.parse("vuequests:quest_backgrounds/" + spriteName);
                check(bundled.contains(spriteId), "Bundled " + shape + " sprite appears in the editor catalog");
                check(QuestEditorResources.read("assets/vuequests/" + textureDirectory + "/" + spriteName + ".png.mcmeta").join() != null,
                        "Bundled " + shape + " has a sprite sidecar");
            }
            QuestEditorAssets.setRoots(List.of(extra, assets));
            var catalog = QuestEditorResources.describe().join().getAsJsonArray("questBackgrounds");
            check(catalog.size() == bundled.size() + 1, "Background discovery includes nested PNGs and deduplicates across asset roots");
            check(catalog.contains(new JsonPrimitive("test:quest_backgrounds/shapes/diamond")), "Custom namespaced sprite appears in the editor catalog");
            check(catalog.contains(new JsonPrimitive(QuestDisplay.DEFAULT_BACKGROUND_SPRITE.toString())), "Bundled square appears in the editor catalog");
            check(QuestEditorResources.read(diamondPath + ".mcmeta").join() != null, "Sprite sidecar is available through the asset reader");
            Files.copy(assets.resolve(squarePath), extra.resolve(diamondPath).resolveSibling("circle.png"));
            QuestEditorAssets.invalidate();
            check(QuestEditorResources.describe().join().getAsJsonArray("questBackgrounds").size() == bundled.size() + 2,
                    "Background catalog updates after asset invalidation");
        } finally {
            QuestEditorAssets.setRoots(List.of());
        }
    }

    private static void verifyHttp() throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); HttpClient client = HttpClient.newHttpClient()) {
            HttpServer listener = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            QuestApiHttpHandler handler = new QuestApiHttpHandler(new QuestApiTestService());
            for (String context : API_CONTEXTS) listener.createContext(context, handler);
            listener.setExecutor(workers);
            listener.start();
            String base = "http://127.0.0.1:" + listener.getAddress().getPort();
            try {
                HttpResponse<String> contract = get(client, base + "/api/openapi.json");
                check(contract.statusCode() == 200, "Bundled contract served");
                var paths = JsonParser.parseString(contract.body()).getAsJsonObject().getAsJsonObject("paths");
                check(paths.keySet().containsAll(WRITE_ROUTES) && paths.keySet().containsAll(READ_ROUTES), "Contract covers every API route");
                check(get(client, base + "/api/create/quest").statusCode() == 405, "GET cannot mutate");
                check(get(client, base + "/api/create/nope").statusCode() == 404, "Unknown route returns 404");
                check(post(client, base + "/api/create/quest", "{}", "application/json", null).statusCode() == 201, "Agent POST without Origin accepted");
                check(post(client, base + "/api/modify/quest/text", "{}", "application/json", base).statusCode() == 200, "Same-origin POST accepted");
                check(post(client, base + "/api/create/quest", "{}", "text/plain", null).statusCode() == 415, "JSON content type required");
                check(post(client, base + "/api/create/quest", "{}", "application/json", "http://127.0.0.1:1").statusCode() == 403, "Different origin port rejected");
                check(post(client, base + "/api/create/quest", "null", "application/json", null).statusCode() == 400, "Null body rejected");
                check(post(client, base + "/api/create/quest", "{bad", "application/json", null).statusCode() == 400, "Malformed JSON rejected");
                check(post(client, base + "/api/create/quest", "{unquoted:1}", "application/json", null).statusCode() == 400, "Unquoted JSON field names rejected");
                check(post(client, base + "/api/create/quest", "{} {}", "application/json", null).statusCode() == 400, "Trailing request data rejected");
                check(post(client, base + "/api/create/quest", "{\"conflict\":true}", "application/json", null).statusCode() == 409, "Save conflicts mapped to 409");
                check(post(client, base + "/api/create/quest", "{\"missing\":true}", "application/json", null).statusCode() == 404, "Missing model mapped to 404");
                HttpResponse<String> timeout = post(client, base + "/api/create/quest", "{\"timeout\":true}", "application/json", null);
                check(timeout.statusCode() == 503 && JsonParser.parseString(timeout.body()).getAsJsonObject().has("error"), "Timeout retains a structured error message");
                check(post(client, base + "/api/create/quest", " ".repeat(QuestApiHttpHandler.MAX_REQUEST_BYTES + 1), "application/json", null).statusCode() == 413, "Oversized request rejected");
                check(post(client, base + "/api/create/quest", "{\"deep\":" + "[".repeat(65) + "0" + "]".repeat(65) + "}", "application/json", null).statusCode() == 400, "Deep request rejected");
                HttpResponse<String> head = client.send(HttpRequest.newBuilder(URI.create(base + "/api/check/sections")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                check(head.statusCode() == 200 && head.body().isEmpty(), "HEAD has no body");
                check(get(client, base + "/api/search?registry=x&registry=y").statusCode() == 400, "Duplicate query parameters rejected");
            } finally { listener.stop(0); }

            String previous = System.getProperty("vuequests.api.token");
            System.setProperty("vuequests.api.token", "verification-token");
            HttpServer secured = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            secured.createContext("/api/", new QuestApiHttpHandler(new QuestApiTestService()));
            secured.setExecutor(workers);
            secured.start();
            String securedUrl = "http://127.0.0.1:" + secured.getAddress().getPort() + "/api/check/sections";
            try {
                check(get(client, securedUrl).statusCode() == 401, "Configured token required");
                var authorized = HttpRequest.newBuilder(URI.create(securedUrl)).header("Authorization", "Bearer verification-token").build();
                check(client.send(authorized, HttpResponse.BodyHandlers.ofString()).statusCode() == 200, "Configured bearer token accepted");
            } finally {
                secured.stop(0);
                if (previous == null) System.clearProperty("vuequests.api.token"); else System.setProperty("vuequests.api.token", previous);
            }
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, String url, String body, String type, String origin) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) request.header("Origin", origin);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void expect(Class<? extends Throwable> type, Runnable action, String message) {
        try { action.run(); } catch (Throwable exception) {
            if (!type.isInstance(exception)) throw new AssertionError(message + ": " + exception, exception);
            checks++;
            return;
        }
        throw new AssertionError(message + ": expected " + type.getSimpleName());
    }
}
