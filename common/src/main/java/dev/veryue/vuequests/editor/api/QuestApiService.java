package dev.veryue.vuequests.editor.api;

import dev.veryue.vuequests.editor.api.models.JsonSchema;
import dev.veryue.vuequests.editor.api.models.ListMode;
import dev.veryue.vuequests.editor.api.models.QuestApiDocument;
import dev.veryue.vuequests.editor.api.models.RegistryDescription;
import dev.veryue.vuequests.editor.api.models.RegistrySearchEntry;
import dev.veryue.vuequests.editor.api.models.requests.CreateDependencyRequest;
import dev.veryue.vuequests.editor.api.models.requests.CreateQuestlineRequest;
import dev.veryue.vuequests.editor.api.models.requests.CreateQuestRequest;
import dev.veryue.vuequests.editor.api.models.requests.CreateSectionRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestIconRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestObjectivesRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestPositionRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestRewardsRequest;
import dev.veryue.vuequests.editor.api.models.requests.ModifyQuestTextRequest;
import dev.veryue.vuequests.editor.api.models.responses.DependencyResult;
import dev.veryue.vuequests.editor.api.models.responses.MutationResponse;
import dev.veryue.vuequests.editor.api.models.responses.PositionResponse;
import dev.veryue.vuequests.editor.api.models.responses.QuestlineResult;
import dev.veryue.vuequests.editor.api.models.responses.QuestReadResponse;
import dev.veryue.vuequests.editor.api.models.responses.QuestResult;
import dev.veryue.vuequests.editor.api.models.responses.RegistrySearchResponse;
import dev.veryue.vuequests.editor.api.models.responses.SectionResult;
import dev.veryue.vuequests.editor.api.models.responses.SectionsResponse;
import dev.veryue.vuequests.editor.api.models.TypeDescription;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.editor.QuestEditorService;
import dev.veryue.vuequests.editor.QuestEditorStorage;
import dev.veryue.vuequests.editor.QuestEditorStorage.Document;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.QuestPosition;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.reward.RewardType;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static dev.veryue.vuequests.editor.QuestEditorConstants.API;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CHECK_OBJECTIVES;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CHECK_QUEST;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CHECK_QUEST_POSITION;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CHECK_REGISTRIES;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CHECK_REWARDS;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CHECK_SECTIONS;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CREATE_DEPENDENCY;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CREATE_QUEST;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CREATE_QUESTLINE;
import static dev.veryue.vuequests.editor.QuestEditorConstants.CREATE_SECTION;
import static dev.veryue.vuequests.editor.QuestEditorConstants.MODIFY_QUEST_ICON;
import static dev.veryue.vuequests.editor.QuestEditorConstants.MODIFY_QUEST_OBJECTIVES;
import static dev.veryue.vuequests.editor.QuestEditorConstants.MODIFY_QUEST_POSITION;
import static dev.veryue.vuequests.editor.QuestEditorConstants.MODIFY_QUEST_REWARDS;
import static dev.veryue.vuequests.editor.QuestEditorConstants.MODIFY_QUEST_TEXT;
import static dev.veryue.vuequests.editor.QuestEditorConstants.SEARCH;

public class QuestApiService {
    private final MinecraftServer server;
    private final QuestEditorService editor;

    public QuestApiService(MinecraftServer server, QuestEditorService editor) {
        this.server = server;
        this.editor = editor;
    }

    public CompletableFuture<String> write(String path, String body) {
        return server.submit(() -> {
            Gson json = json();
            return switch (path) {
                case CREATE_SECTION -> mutate(body, CreateSectionRequest.class, CreateSectionRequest::revision, QuestApiService::createSection, json);
                case CREATE_QUEST -> mutate(body, CreateQuestRequest.class, CreateQuestRequest::revision, QuestApiService::createQuest, json);
                case CREATE_QUESTLINE -> mutate(body, CreateQuestlineRequest.class, CreateQuestlineRequest::revision, (document, request) -> {
                    List<CreateSectionRequest> sections = list(request.sections());
                    List<CreateQuestRequest> quests = list(request.quests());
                    if (sections.isEmpty() && quests.isEmpty()) throw new IllegalArgumentException("Supply sections or quests.");
                    List<UUID> sectionIds = sections.stream().map(section -> createSection(document, section).sectionId()).toList();
                    List<QuestResult> results = quests.stream().map(quest -> createQuest(document, quest)).toList();
                    return new QuestlineResult(sectionIds, results);
                }, json);
                case CREATE_DEPENDENCY -> mutate(body, CreateDependencyRequest.class, CreateDependencyRequest::revision, (document, request) -> {
                    Quest quest = document.quest(request.questId());
                    document.quest(request.dependencyId());
                    List<UUID> dependencies = new ArrayList<>(quest.dependencies());
                    if (!dependencies.contains(request.dependencyId())) dependencies.add(request.dependencyId());
                    document.update(copy(quest, quest.position(), quest.display(), quest.objectives(), quest.rewards(), dependencies));
                    return new DependencyResult(quest.questId(), request.dependencyId());
                }, json);
                case MODIFY_QUEST_TEXT -> mutate(body, ModifyQuestTextRequest.class, ModifyQuestTextRequest::revision, (document, request) -> {
                    if (request.title() == null && request.subtitle() == null && request.description() == null && request.tooltip() == null) {
                        throw new IllegalArgumentException("Supply at least one text field; use an empty string to clear it.");
                    }
                    Quest quest = document.quest(request.questId());
                    QuestDisplay old = quest.display();
                    QuestDisplay display = new QuestDisplay(request.title() == null ? old.title() : request.title(),
                            request.subtitle() == null ? old.subtitle() : request.subtitle(),
                            request.description() == null ? old.description() : request.description(), old.icon(),
                            request.tooltip() == null ? old.tooltip() : request.tooltip());
                    Quest updated = copy(quest, quest.position(), display, quest.objectives(), quest.rewards(), quest.dependencies());
                    document.update(updated);
                    return QuestResult.of(updated);
                }, json);
                case MODIFY_QUEST_OBJECTIVES -> mutate(body, ModifyQuestObjectivesRequest.class, ModifyQuestObjectivesRequest::revision, (document, request) -> {
                    Quest quest = document.quest(request.questId());
                    Quest updated = copy(quest, quest.position(), quest.display(), merge(quest.objectives(), request.objectives(), request.mode()), quest.rewards(), quest.dependencies());
                    document.update(updated);
                    return QuestResult.of(updated);
                }, json);
                case MODIFY_QUEST_REWARDS -> mutate(body, ModifyQuestRewardsRequest.class, ModifyQuestRewardsRequest::revision, (document, request) -> {
                    Quest quest = document.quest(request.questId());
                    Quest updated = copy(quest, quest.position(), quest.display(), quest.objectives(), merge(quest.rewards(), request.rewards(), request.mode()), quest.dependencies());
                    document.update(updated);
                    return QuestResult.of(updated);
                }, json);
                case MODIFY_QUEST_ICON -> mutate(body, ModifyQuestIconRequest.class, ModifyQuestIconRequest::revision, (document, request) -> {
                    if (request.icon() == null) throw new IllegalArgumentException("Supply icon.");
                    Quest quest = document.quest(request.questId());
                    QuestDisplay old = quest.display();
                    QuestDisplay display = new QuestDisplay(old.title(), old.subtitle(), old.description(), item(request.icon(), Items.BOOK), old.tooltip());
                    Quest updated = copy(quest, quest.position(), display, quest.objectives(), quest.rewards(), quest.dependencies());
                    document.update(updated);
                    return QuestResult.of(updated);
                }, json);
                case MODIFY_QUEST_POSITION -> mutate(body, ModifyQuestPositionRequest.class, ModifyQuestPositionRequest::revision, (document, request) -> {
                    if (request.position() == null) throw new IllegalArgumentException("Supply position.");
                    Quest quest = document.quest(request.questId());
                    Quest updated = copy(quest, request.position().toPosition(), quest.display(), quest.objectives(), quest.rewards(), quest.dependencies());
                    document.update(updated);
                    return QuestResult.of(updated);
                }, json);
                default -> throw new QuestApiException(404, "Endpoint not found.");
            };
        }).thenCompose(future -> future).thenCompose(response -> server.submit(() -> json().toJson(response)));
    }

    private <R, T> CompletableFuture<MutationResponse<T>> mutate(String body, Class<R> type, Function<R, String> revision,
                                                               BiFunction<QuestApiDocument, R, T> change, Gson json) {
        R request = json.fromJson(body, type);
        if (request == null) throw new IllegalArgumentException("Supply a JSON request object.");
        return editor.mutate(revision.apply(request), document -> change.apply(document, request));
    }

    public CompletableFuture<String> read(String path, Map<String, String> parameters) {
        return server.submit(() -> {
            Object response;
            switch (path) {
                case CHECK_OBJECTIVES -> response = VeryUERegistries.QUEST_OBJECTIVE_TYPES.keySet().stream().sorted().map(id -> {
                    var type = VeryUERegistries.QUEST_OBJECTIVE_TYPES.get(id);
                    return describe(id, type.codec(), type.schema());
                }).toList();
                case CHECK_REWARDS -> response = VeryUERegistries.REWARD_TYPES.keySet().stream().sorted().map(id -> {
                    var type = VeryUERegistries.REWARD_TYPES.get(id);
                    return describe(id, type.codec(), type.schema());
                }).toList();
                case CHECK_REGISTRIES -> response = List.of(
                        new RegistryDescription("minecraft:item", BuiltInRegistries.ITEM.size(), List.of("section.icon", "quest.display.icon", "vuequests:collect_item.item", "vuequests:item.item")),
                        new RegistryDescription("minecraft:entity_type", BuiltInRegistries.ENTITY_TYPE.size(), List.of("vuequests:entity.entity")),
                        new RegistryDescription("minecraft:data_component_type", BuiltInRegistries.DATA_COMPONENT_TYPE.size(), List.of("item.components")));
                case SEARCH -> response = search(parameters);
                case CHECK_SECTIONS, CHECK_QUEST, CHECK_QUEST_POSITION -> {
                    Document stored = new QuestEditorStorage(server.getWorldPath(LevelResource.DATAPACK_DIR)).read(server.getResourceManager());
                    QuestApiDocument document = new QuestApiDocument(stored.sections(), server.registryAccess().createSerializationContext(JsonOps.INSTANCE), PlatformHelper.INSTANCE::isModLoaded);
                    if (path.equals(CHECK_SECTIONS)) response = new SectionsResponse(document.sections(), stored.revision());
                    else {
                        Quest quest = document.quest(UUID.fromString(required(parameters, "questId")));
                        response = path.equals(CHECK_QUEST) ? new QuestReadResponse(quest, stored.revision())
                                : new PositionResponse(quest.questId(), quest.sectionUUID(), quest.position().x(), quest.position().y(), stored.revision());
                    }
                }
                default -> throw new QuestApiException(404, "Endpoint not found.");
            }
            return json().toJson(response);
        });
    }

    private Gson json() { return QuestApiJson.create(server.registryAccess().createSerializationContext(JsonOps.INSTANCE)); }

    static SectionResult createSection(QuestApiDocument document, CreateSectionRequest request) {
        if (request.name() == null) throw new IllegalArgumentException("Supply name.");
        UUID id = request.sectionId() == null ? UUID.randomUUID() : request.sectionId();
        document.add(new Section(id, request.name(), item(request.icon(), Items.BOOK), List.of()));
        return new SectionResult(id);
    }

    static QuestResult createQuest(QuestApiDocument document, CreateQuestRequest request) {
        document.section(request.sectionId());
        UUID id = request.questId() == null ? UUID.randomUUID() : request.questId();
        QuestDisplay display = new QuestDisplay(component(request.title(), "New quest"), component(request.subtitle(), ""),
                component(request.description(), ""), item(request.icon(), Items.BOOK), request.tooltip() == null ? "" : request.tooltip());
        Quest quest = new Quest(request.position() == null ? new QuestPosition(0, 0) : request.position().toPosition(), display,
                request.sectionId(), id, list(request.objectives()), list(request.rewards()), list(request.dependencies()));
        document.add(quest);
        return QuestResult.of(quest);
    }

    private static TypeDescription describe(ResourceLocation id, MapCodec<?> codec, JsonSchema schema) {
        List<String> fields = codec.keys(JsonOps.INSTANCE).map(JsonElement::getAsString).toList();
        boolean complete = schema != null;
        if (schema == null) {
            Map<String, JsonSchema> properties = new LinkedHashMap<>();
            properties.put("type", JsonSchema.type("string").values(id.toString()));
            fields.forEach(field -> properties.put(field, new JsonSchema()));
            schema = JsonSchema.object(properties, List.of("type"))
                    .description("This codec did not register schema metadata. Field names are known; types and required fields must be supplied by the registering mod.");
        }
        return new TypeDescription(id.toString(), fields, schema, complete);
    }

    public static RegistrySearchResponse search(Map<String, String> parameters) {
        String registry = required(parameters, "registry");
        Set<ResourceLocation> ids = switch (registry) {
            case "minecraft:item" -> BuiltInRegistries.ITEM.keySet();
            case "minecraft:entity_type" -> BuiltInRegistries.ENTITY_TYPE.keySet();
            case "minecraft:data_component_type" -> BuiltInRegistries.DATA_COMPONENT_TYPE.keySet();
            default -> throw new IllegalArgumentException("Unknown registry; use " + API + CHECK_REGISTRIES + ".");
        };
        String namespace = parameters.getOrDefault("namespace", "").toLowerCase(Locale.ROOT);
        String path = parameters.getOrDefault("path", "").toLowerCase(Locale.ROOT);
        int offset = integer(parameters, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = integer(parameters, "limit", 100, 1, 500);
        List<ResourceLocation> matches = ids.stream().filter(id -> id.getNamespace().contains(namespace) && id.getPath().contains(path)).sorted().toList();
        return new RegistrySearchResponse(registry, matches.size(), offset, limit, matches.stream().skip(offset).limit(limit)
                .map(id -> new RegistrySearchEntry(id.toString(), id.getNamespace(), id.getPath())).toList());
    }

    private static int integer(Map<String, String> parameters, String name, int fallback, int minimum, int maximum) {
        int value = parameters.containsKey(name) ? Integer.parseInt(parameters.get(name)) : fallback;
        if (value < minimum || value > maximum) throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        return value;
    }

    private static String required(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Supply " + name + ".");
        return value;
    }

    private static Item item(String id, Item fallback) {
        if (id == null) return fallback;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) throw new IllegalArgumentException("Unknown item " + id);
        return BuiltInRegistries.ITEM.get(location);
    }

    private static Component component(Component value, String fallback) { return value == null ? Component.literal(fallback) : value; }
    private static <T> List<T> list(List<T> values) {
        if (values == null) return List.of();
        if (values.stream().anyMatch(value -> value == null)) throw new IllegalArgumentException("List entries cannot be null.");
        return List.copyOf(values);
    }

    private static <T> List<T> merge(List<T> old, List<T> supplied, ListMode mode) {
        if (supplied == null || supplied.stream().anyMatch(value -> value == null)) throw new IllegalArgumentException("Supply a list of objective/reward objects.");
        List<T> result = new ArrayList<>(mode == ListMode.REPLACE ? List.of() : old);
        result.addAll(supplied);
        return List.copyOf(result);
    }

    private static Quest copy(Quest quest, QuestPosition position, QuestDisplay display,
                              List<? extends QuestObjective<?>> objectives,
                              List<? extends RewardType<?>> rewards, List<UUID> dependencies) {
        return new Quest(position, display, quest.sectionUUID(), quest.questId(), List.copyOf(objectives), List.copyOf(rewards), List.copyOf(dependencies));
    }
}
