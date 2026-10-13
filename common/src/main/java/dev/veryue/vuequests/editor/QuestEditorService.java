package dev.veryue.vuequests.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.platform.PlatformHelper;
import dev.veryue.vuequests.VeryUERegistries;
import dev.veryue.vuequests.editor.QuestEditorStorage.Document;
import net.minecraft.SharedConstants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class QuestEditorService {

    // Section bodies use the game's extensible codecs; a Gson mirror would discard fields supplied by other mods.
    public record SaveRequest(String revision, List<JsonObject> sections) { }
    public record Snapshot(List<JsonObject> sections, String revision, String minecraftVersion, String assetRevision, Catalog catalog) { }
    public record RegistryEntry(String id, String name, String item) { }
    public record TypeEntry(String id, List<String> fields) { }
    public record Catalog(List<RegistryEntry> items, List<RegistryEntry> blocks, List<RegistryEntry> entities,
                          List<TypeEntry> objectiveTypes, List<TypeEntry> rewardTypes) { }

    public static class ConflictException extends IllegalStateException {
        public ConflictException(String message) { super(message); }
    }

    private final MinecraftServer server;
    private final AtomicBoolean saving = new AtomicBoolean();

    public QuestEditorService(MinecraftServer server) {
        this.server = server;
    }

    public CompletableFuture<Snapshot> load() {
        return server.submit(() -> {
            Document document = new QuestEditorStorage(server.getWorldPath(LevelResource.DATAPACK_DIR)).read(server.getResourceManager());
            return new Snapshot(document.sections(), document.revision(), SharedConstants.getCurrentVersion().getName(),
                    QuestEditorAssets.revision(), createCatalog());
        });
    }

    public CompletableFuture<Snapshot> save(SaveRequest request) {
        if (!saving.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new ConflictException("A save is already in progress."));
        }
        return server.submit(() -> {
            QuestEditorStorage storage = new QuestEditorStorage(server.getWorldPath(LevelResource.DATAPACK_DIR));
            Document previous = storage.read(server.getResourceManager());
            if (!previous.revision().equals(request.revision())) {
                throw new ConflictException("Quests changed since this editor loaded. Reload before saving.");
            }
            List<JsonObject> sections = request.sections();
            validate(sections, server.registryAccess().createSerializationContext(JsonOps.INSTANCE), PlatformHelper.INSTANCE::isModLoaded);
            try {
                storage.save(previous, sections);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            server.getPackRepository().reload();
            List<String> packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
            packs.remove(QuestEditorStorage.PACK_ID);
            packs.add(QuestEditorStorage.PACK_ID);
            return packs;
        }).thenCompose(server::reloadResources).thenCompose(ignored -> load())
                .whenComplete((result, failure) -> saving.set(false));
    }

    static void validate(List<JsonObject> sections, RegistryOps<JsonElement> ops, Predicate<String> isModLoaded) {
        if (sections == null || sections.size() > 2048) {
            throw new IllegalArgumentException("Supply a quest section list with at most 2048 sections.");
        }
        Set<UUID> sectionIds = new HashSet<>();
        Map<UUID, Quest> quests = new HashMap<>();
        var sectionIdCodec = UUIDUtil.STRING_CODEC.fieldOf("section_id").codec();
        for (JsonObject json : sections) {
            String requiredMod = Section.MOD_LOADED_CODEC.codec().parse(ops, json).getOrThrow(IllegalArgumentException::new);
            boolean available = requiredMod.isEmpty() || isModLoaded.test(requiredMod);
            Section section = available ? Section.CODEC.parse(ops, json).getOrThrow(IllegalArgumentException::new) : null;
            UUID sectionId = section == null ? sectionIdCodec.parse(ops, json).getOrThrow(IllegalArgumentException::new) : section.sectionUUID();
            if (!sectionIds.add(sectionId)) {
                throw new IllegalArgumentException("Duplicate section UUID " + sectionId);
            }
            // Match the loader: unavailable mods may supply registry values that cannot be decoded here.
            if (section == null) continue;
            for (Quest quest : section.questList()) {
                UUID id = quest.questId();
                if (!sectionId.equals(quest.sectionUUID())) {
                    throw new IllegalArgumentException("Quest " + id + " belongs to a different section.");
                }
                if (quests.putIfAbsent(id, quest) != null) {
                    throw new IllegalArgumentException("Duplicate quest UUID " + id);
                }
            }
        }
        Map<UUID, Integer> remaining = new HashMap<>();
        Map<UUID, List<UUID>> dependents = new HashMap<>();
        quests.forEach((id, quest) -> {
            remaining.put(id, quest.dependencies().size());
            for (UUID requirement : quest.dependencies()) {
                if (!quests.containsKey(requirement)) {
                    throw new IllegalArgumentException("Quest " + id + " references an unavailable prerequisite.");
                }
                dependents.computeIfAbsent(requirement, ignored -> new ArrayList<>()).add(id);
            }
        });
        ArrayDeque<UUID> ready = new ArrayDeque<>();
        remaining.forEach((id, count) -> { if (count == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            UUID next = ready.removeFirst();
            visited++;
            for (UUID dependent : dependents.getOrDefault(next, List.of())) {
                if (remaining.compute(dependent, (id, count) -> count - 1) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (visited != quests.size()) {
            throw new IllegalArgumentException("Quest dependencies must not form a cycle.");
        }
    }

    private static Catalog createCatalog() {
        var items = BuiltInRegistries.ITEM.keySet().stream().sorted().map(id ->
                new RegistryEntry(id.toString(), BuiltInRegistries.ITEM.get(id).getDescription().getString(), null)).toList();
        var blocks = BuiltInRegistries.BLOCK.keySet().stream().sorted().map(id -> {
            var block = BuiltInRegistries.BLOCK.get(id);
            return new RegistryEntry(id.toString(), block.getName().getString(),
                    block.asItem() == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(block.asItem()).toString());
        }).toList();
        var entities = BuiltInRegistries.ENTITY_TYPE.keySet().stream().sorted().map(id -> {
            var entity = BuiltInRegistries.ENTITY_TYPE.get(id);
            SpawnEggItem egg = SpawnEggItem.byId(entity);
            return new RegistryEntry(id.toString(), entity.getDescription().getString(), egg == null ? null : BuiltInRegistries.ITEM.getKey(egg).toString());
        }).toList();
        var objectives = VeryUERegistries.QUEST_OBJECTIVE_TYPES.keySet().stream().sorted().map(id ->
                new TypeEntry(id.toString(), VeryUERegistries.QUEST_OBJECTIVE_TYPES.get(id).codec().keys(JsonOps.INSTANCE).map(JsonElement::getAsString).toList())).toList();
        var rewards = VeryUERegistries.REWARD_TYPES.keySet().stream().sorted().map(id ->
                new TypeEntry(id.toString(), VeryUERegistries.REWARD_TYPES.get(id).codec().keys(JsonOps.INSTANCE).map(JsonElement::getAsString).toList())).toList();
        return new Catalog(items, blocks, entities, objectives, rewards);
    }
}
