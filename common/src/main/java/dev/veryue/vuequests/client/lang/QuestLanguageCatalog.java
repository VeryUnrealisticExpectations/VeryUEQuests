package dev.veryue.vuequests.client.lang;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class QuestLanguageCatalog {
    private static final ThreadLocal<Language> PREVIEW_LANGUAGE = new ThreadLocal<>();

    private final Map<UUID, Map<String, QuestLanguageEntry>> sections = new LinkedHashMap<>();
    private final Map<UUID, Map<String, QuestLanguageEntry>> quests = new LinkedHashMap<>();

    public static Language languageOr(Language fallback) {
        Language language = PREVIEW_LANGUAGE.get();
        return language == null ? fallback : language;
    }

    public static <T> T withLanguage(Language language, Supplier<T> action) {
        Language previous = PREVIEW_LANGUAGE.get();
        PREVIEW_LANGUAGE.set(language);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                PREVIEW_LANGUAGE.remove();
            } else {
                PREVIEW_LANGUAGE.set(previous);
            }
        }
    }

    static QuestLanguageCatalog decode(JsonElement value, DynamicOps<JsonElement> ops) {
        JsonObject document = object(value, "Quest language catalog");
        QuestLanguageCatalog catalog = new QuestLanguageCatalog();
        decodeGroup(document, "sections", Set.of("name"), ops, catalog.sections);
        decodeGroup(document, "quests", Set.of("title", "subtitle", "description"), ops, catalog.quests);
        return catalog;
    }

    private static void decodeGroup(JsonObject document, String key, Set<String> fields, DynamicOps<JsonElement> ops,
                                    Map<UUID, Map<String, QuestLanguageEntry>> destination) {
        if (!document.has(key)) {
            return;
        }
        object(document.get(key), key).entrySet().forEach(entry -> {
            String location = key + "." + entry.getKey();
            UUID id = UUID.fromString(entry.getKey());
            Map<String, QuestLanguageEntry> text = new LinkedHashMap<>();
            object(entry.getValue(), location).entrySet().forEach(field -> {
                String fieldLocation = location + "." + field.getKey();
                if (!fields.contains(field.getKey())) {
                    throw new IllegalArgumentException("Unknown quest language field " + fieldLocation);
                }
                JsonElement json = field.getValue();
                checkDepth(json, 0);
                Component component = ComponentSerialization.CODEC.parse(ops, json)
                        .getOrThrow(error -> new IllegalArgumentException(fieldLocation + ": " + error));
                text.put(field.getKey(), new QuestLanguageEntry(json, component));
            });
            if (destination.putIfAbsent(id, text) != null) {
                throw new IllegalArgumentException("Duplicate quest language entry " + location);
            }
        });
    }

    private static void checkDepth(JsonElement element, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("Component nesting exceeds 64 levels.");
        }
        if (element.isJsonObject()) {
            element.getAsJsonObject().asMap().values().forEach(child -> checkDepth(child, depth + 1));
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> checkDepth(child, depth + 1));
        }
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be a JSON object.");
        }
        return element.getAsJsonObject();
    }

    public void overlay(QuestLanguageCatalog overrides) {
        overlayGroup(sections, overrides.sections);
        overlayGroup(quests, overrides.quests);
    }

    private static void overlayGroup(Map<UUID, Map<String, QuestLanguageEntry>> target,
                                     Map<UUID, Map<String, QuestLanguageEntry>> overrides) {
        overrides.forEach((id, fields) -> target.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).putAll(fields));
    }

    public QuestData buildView(QuestData source) {
        ImmutableMap.Builder<UUID, Quest> questIndex = ImmutableMap.builder();
        source.quests().forEach((id, quest) -> {
            QuestDisplay display = quest.display();
            QuestDisplay localized = new QuestDisplay(resolve(quests, id, "title", display.title()),
                    resolve(quests, id, "subtitle", display.subtitle()), resolve(quests, id, "description", display.description()),
                    display.icon(), display.tooltip(), display.backgroundSprite());
            questIndex.put(id, new Quest(quest.position(), localized, quest.sectionUUID(), id,
                    quest.objectives(), quest.rewards(), quest.dependencies()));
        });
        Map<UUID, Quest> localizedQuests = questIndex.buildOrThrow();
        ImmutableMap.Builder<UUID, Section> localizedSections = ImmutableMap.builder();
        source.sections().forEach((id, section) -> localizedSections.put(id,
                new Section(id, resolve(sections, id, "name", section.name()), section.icon(), section.disabled(),
                        section.modLoaded(), section.priority(),
                        section.questList().stream().map(quest -> localizedQuests.get(quest.questId())).toList())));
        return new QuestData(localizedSections.buildOrThrow(), localizedQuests);
    }

    private static Component resolve(Map<UUID, Map<String, QuestLanguageEntry>> group, UUID id, String field, Component fallback) {
        QuestLanguageEntry text = group.getOrDefault(id, Map.of()).get(field);
        return text == null ? fallback : text.component();
    }

    JsonObject toJson() {
        JsonObject document = new JsonObject();
        document.add("sections", encodeGroup(sections));
        document.add("quests", encodeGroup(quests));
        return document;
    }

    private static JsonObject encodeGroup(Map<UUID, Map<String, QuestLanguageEntry>> entries) {
        JsonObject group = new JsonObject();
        entries.forEach((id, fields) -> {
            JsonObject text = new JsonObject();
            fields.forEach((key, value) -> text.add(key, value.json()));
            group.add(id.toString(), text);
        });
        return group;
    }

}
