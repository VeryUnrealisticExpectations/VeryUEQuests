package dev.veryue.vuequests.editor.api.models.requests;

import dev.veryue.vuequests.editor.api.models.ListMode;
import dev.veryue.vuequests.models.objective.QuestObjective;
import java.util.List;
import java.util.UUID;

public record ModifyQuestObjectivesRequest(UUID questId, List<QuestObjective<?>> objectives,
                                          ListMode mode, String revision) { }
