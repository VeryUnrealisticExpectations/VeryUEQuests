package dev.veryue.vuequests.editor.api.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.veryue.vuequests.editor.api.QuestApiException;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.resources.RegistryOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class QuestApiDocument {
    private final Map<UUID, JsonObject> originals = new LinkedHashMap<>();
    private final Map<UUID, Section> sections = new LinkedHashMap<>();
    private final Set<UUID> changed = new HashSet<>();
    private final RegistryOps<JsonElement> ops;

    public QuestApiDocument(List<JsonObject> raw, RegistryOps<JsonElement> ops, Predicate<String> isModLoaded) {
        this.ops = ops;
        for (JsonObject json : raw) {
            UUID id = UUID.fromString(json.get("section_id").getAsString());
            originals.put(id, json);
            String mod = Section.MOD_LOADED_CODEC.codec().parse(ops, json).getOrThrow(IllegalArgumentException::new);
            if (mod.isEmpty() || isModLoaded.test(mod)) {
                sections.put(id, Section.CODEC.parse(ops, json).getOrThrow(IllegalArgumentException::new));
            }
        }
    }

    public List<Section> sections() { return List.copyOf(sections.values()); }

    public Section section(UUID id) {
        if (id == null) throw new IllegalArgumentException("Supply sectionId.");
        Section section = sections.get(id);
        if (section == null) throw new QuestApiException(originals.containsKey(id) ? 409 : 404,
                "Section " + id + " is unavailable or does not exist.");
        if (section.disabled()) throw new QuestApiException(409, "Section " + id + " is disabled.");
        return section;
    }

    public Quest quest(UUID id) {
        if (id == null) throw new IllegalArgumentException("Supply questId.");
        for (Section section : sections.values()) {
            for (Quest quest : section.questList()) {
                if (quest.questId().equals(id)) {
                    section(section.sectionUUID());
                    return quest;
                }
            }
        }
        throw new QuestApiException(404, "Quest " + id + " does not exist in an available section.");
    }

    public void add(Section section) {
        if (originals.containsKey(section.sectionUUID()) || sections.containsKey(section.sectionUUID())) {
            throw new QuestApiException(409, "Section UUID already exists: " + section.sectionUUID());
        }
        sections.put(section.sectionUUID(), section);
        changed.add(section.sectionUUID());
    }

    public void add(Quest quest) {
        if (sections.values().stream().flatMap(section -> section.questList().stream()).anyMatch(existing -> existing.questId().equals(quest.questId()))) {
            throw new QuestApiException(409, "Quest UUID already exists: " + quest.questId());
        }
        Section section = section(quest.sectionUUID());
        List<Quest> quests = new ArrayList<>(section.questList());
        quests.add(quest);
        replace(section, quests);
    }

    public void update(Quest quest) {
        Section section = section(quest.sectionUUID());
        replace(section, section.questList().stream().map(existing -> existing.questId().equals(quest.questId()) ? quest : existing).toList());
    }

    private void replace(Section section, List<Quest> quests) {
        sections.put(section.sectionUUID(), new Section(section.sectionUUID(), section.name(), section.icon(),
                section.disabled(), section.modLoaded(), List.copyOf(quests)));
        changed.add(section.sectionUUID());
    }

    public List<JsonObject> encode() {
        Map<UUID, JsonObject> result = new LinkedHashMap<>(originals);
        for (UUID id : changed) {
            result.put(id, Section.CODEC.encodeStart(ops, sections.get(id)).getOrThrow(IllegalArgumentException::new).getAsJsonObject());
        }
        return List.copyOf(result.values());
    }
}
