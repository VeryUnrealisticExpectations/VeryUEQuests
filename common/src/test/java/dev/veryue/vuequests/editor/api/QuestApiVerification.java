package dev.veryue.vuequests.editor.api;

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
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.editor.QuestEditorAssets;
import dev.veryue.vuequests.editor.QuestEditorResources;
import dev.veryue.vuequests.editor.QuestEditorService;
import dev.veryue.vuequests.editor.QuestEditorStorage;
import dev.veryue.vuequests.editor.QuestEditorStorage.Document;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
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
        VeryUERegistries.QUEST_OBJECTIVE_TYPES.freeze();
        VeryUERegistries.REWARD_TYPES.freeze();
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        Gson json = QuestApiJson.create(ops);
        verifyModels(json, ops);
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
        check(request.objectives().get(0).getIconStack().getCount() == 5, "Required item count preserved");
        check(request.objectives().get(0).getIconStack().getHoverName().getString().equals("Quest log"), "Native item component preserved");
        check(request.objectives().get(1).getValue().equals(42), "Custom codec fields preserved");
        check(request.objectives().get(0).getObjectiveId() != null && request.rewards().get(0).getRewardId() != null, "Generated child UUIDs");
        QuestApiDocument document = new QuestApiDocument(List.of(), ops, ignored -> false);
        QuestApiService.createSection(document, new CreateSectionRequest(sectionId, Component.literal("Section"), "minecraft:book", null));
        QuestResult first = QuestApiService.createQuest(document, request);
        check(first.questId().equals(firstId) && first.position().x() == -80, "Quest creation result and position");
        expect(QuestApiException.class, () -> QuestApiService.createQuest(document, request), "Duplicate creation rejected");
        CreateQuestRequest second = json.fromJson("""
                {"sectionId":"%s","questId":"%s","dependencies":["%s"],"objectives":[{"type":"vuequests:entity","entity":"minecraft:zombie"}],
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

    private static void verifyProgression(Quest first, Quest second) {
        UUID firstId = first.questId();
        UUID secondId = second.questId();
        UUID thirdId = UUID.randomUUID();
        Quest third = new Quest(first.position(), first.display(), first.sectionUUID(), thirdId,
                List.of(), List.of(), List.of(firstId, secondId));
        QuestData quests = new QuestData(Map.of(), Map.of(firstId, first, secondId, second, thirdId, third));
        PlayerUser player = new PlayerUser(UUID.randomUUID());
        PlayerUser otherPlayer = new PlayerUser(UUID.randomUUID());
        var collectObjective = first.objectives().getFirst();
        var entityObjective = second.objectives().getFirst();
        check(quests.accessibleQuests(player).equals(List.of(first)), "Root quests are accessible without progress");
        check(quests.accessibleQuests(player, entityObjective.getType()).isEmpty(), "Typed lookup excludes locked quests");
        check(quests.handleQuestTrigger(player, collectObjective.getType(), null).equals(List.of(collectObjective)),
                "Trigger selects only the matching objective from a mixed quest");
        check(quests.handleQuestTrigger(player, QuestApiTestObjective.LEGACY, null).isEmpty(), "An unused trigger type has no matches");
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
        expect(IllegalArgumentException.class, () -> new QuestData(Map.of(), Map.of(secondId, second)), "Snapshot rejects missing prerequisites");
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
