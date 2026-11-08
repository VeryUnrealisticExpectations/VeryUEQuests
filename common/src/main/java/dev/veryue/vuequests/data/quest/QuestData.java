package dev.veryue.vuequests.data.quest;

import com.google.common.collect.ImmutableMap;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.QuestObjectiveProgress;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.objective.QuestObjectiveSerializer;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuestData {
    public static final QuestData EMPTY = new QuestData(ImmutableMap.of(), ImmutableMap.of());

    private final Map<UUID, Section> sections;
    private final Map<UUID, Quest> quests;
    private final List<Quest> rootQuests;
    private final Map<UUID, List<UUID>> dependents;
    private final Map<QuestObjectiveSerializer<?>, List<Quest>> questsByObjectiveType;

    public QuestData(Map<UUID, Section> sections, Map<UUID, Quest> quests) {
        this.sections = sections;
        this.quests = quests;
        this.rootQuests = quests.values().stream()
                .filter(quest -> quest.dependencies().isEmpty())
                .toList();
        this.dependents = indexDependents(this.quests);
        this.questsByObjectiveType = indexObjectiveTypes(this.quests);
    }

    public Map<UUID, Section> sections() {
        return sections;
    }

    public Map<UUID, Quest> quests() {
        return quests;
    }

    public List<Quest> questsByObjectiveType(QuestObjectiveSerializer<?> objectiveType) {
        return questsByObjectiveType.getOrDefault(objectiveType, List.of());
    }

    public List<UUID> dependentsOf(UUID questId) {
        return dependents.getOrDefault(questId, List.of());
    }


    public List<QuestObjective<?, ?>> handleQuestTrigger(User user, QuestObjectiveSerializer<?> objectiveType, ObjectiveContext context) {
        List<QuestObjective<?, ?>> matchingObjectives = new ArrayList<>();
        for (Quest quest : questsByObjectiveType(objectiveType)) {
            if (!prerequisitesMet(quest.questId(), user)) {
                continue;
            }

            int quests = quest.objectives().size();
            int objectivesCompleted = 0;
            for (int i = 0; i < quests; i++) {
                QuestObjective<?, ?> objective = quest.objectives().get(i);
                boolean completed = user.hasCompletedObjective(objective);
                if (completed) {
                    objectivesCompleted++;
                }
                if (!objectiveType.equals(objective.getType()) || completed) {
                    // todo the completed could be a bug source maybe..
                    continue;
                }

                matchingObjectives.add(objective);
                completed = handleObjectiveTrigger(user, objective, context);
                if (completed) {
                    objectivesCompleted++;
                }
                if (objectivesCompleted == quests) {
                    // then the quest is complete.
                    user.grantQuestCompletion(quest);
                }
            }
        }

        return matchingObjectives;
    }

    private <T, TYPE> boolean handleObjectiveTrigger(User user, QuestObjective<T, TYPE> objective, ObjectiveContext context) {
        boolean completedObjective = false;
        QuestObjectiveProgress<T, TYPE> progress = user.incrementObjectiveProgress(objective, context);
        if (progress.isComplete(objective)) {
            user.completeObjective(objective);
            completedObjective = true;
        }
        // todo; then we also need to update the client player when they're on a server
        // todo probably a sync payload again.
        return completedObjective;
    }

    public boolean prerequisitesMet(UUID questId, User user) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return false;
        }
        for (UUID prerequisite : quest.dependencies()) {
            if (!user.hasCompletedQuest(quests.get(prerequisite))) {
                return false;
            }
        }
        return true;
    }

    public List<Quest> accessibleQuests(User user) {
        Set<UUID> completedQuests = user.getCompletedQuests();
        if (completedQuests.isEmpty()) {
            return rootQuests;
        }

        Map<UUID, Integer> completedPrerequisites = new HashMap<>();
        for (UUID completedQuestId : completedQuests) {
            for (UUID dependentId : dependentsOf(completedQuestId)) {
                completedPrerequisites.merge(dependentId, 1, Integer::sum);
            }
        }

        List<Quest> accessible = new ArrayList<>(rootQuests);
        completedPrerequisites.forEach((id, count) -> {
            Quest quest = quests.get(id);
            if (count == quest.dependencies().size()) {
                accessible.add(quest);
            }
        });
        return accessible;
    }

    public List<Quest> accessibleQuests(User user, QuestObjectiveSerializer<?> objectiveType) {
        return questsByObjectiveType(objectiveType).stream()
                .filter(quest -> prerequisitesMet(quest.questId(), user))
                .toList();
    }

    public List<UUID> availableDependents(UUID completedQuestId, User user) {
        Quest completedQuest = quests.get(completedQuestId);
        if (completedQuest == null || !user.hasCompletedQuest(completedQuest)) {
            return List.of();
        }
        return dependentsOf(completedQuestId).stream()
                .filter(id -> !user.hasCompletedQuest(quests.get(id)) && prerequisitesMet(id, user))
                .toList();
    }

    /**
     * Groups quests by objective type, listing each quest once per type.
     * Triggers can use these groups to limit prerequisite checks to quests matching the event.
     */
    private Map<QuestObjectiveSerializer<?>, List<Quest>> indexObjectiveTypes(Map<UUID, Quest> quests) {
        Map<QuestObjectiveSerializer<?>, List<Quest>> index = new HashMap<>();
        for (Quest quest : quests.values()) {
            quest.objectives().stream()
                    .map(QuestObjective::getType)
                    .distinct()
                    .forEach(type -> index.computeIfAbsent(type, ignored -> new ArrayList<>()).add(quest));
        }
        return index;
    }

    /**
     * Validates dependencies and maps each prerequisite UUID to its direct dependent quest UUIDs.
     * Reusing these links narrows completion and access checks to quests connected to the player's progress.
     */
    public static Map<UUID, List<UUID>> indexDependents(Map<UUID, Quest> quests) {
        Map<UUID, Integer> remaining = new HashMap<>();
        Map<UUID, List<UUID>> dependents = new HashMap<>();
        ArrayDeque<UUID> ready = new ArrayDeque<>();
        quests.forEach((id, quest) -> {
            List<UUID> prerequisites = quest.dependencies();
            if (new HashSet<>(prerequisites).size() != prerequisites.size()) {
                throw new IllegalArgumentException("Duplicate dependency on quest " + id);
            }
            remaining.put(id, prerequisites.size());
            if (prerequisites.isEmpty()) {
                ready.add(id);
            }
            for (UUID prerequisite : prerequisites) {
                if (!quests.containsKey(prerequisite)) {
                    throw new IllegalArgumentException("Quest " + id + " references an unavailable prerequisite " + prerequisite);
                }
                dependents.computeIfAbsent(prerequisite, ignored -> new ArrayList<>()).add(id);
            }
        });

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

        return dependents;
    }
}
