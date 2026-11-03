package dev.veryue.vuequests.editor.api.models.responses;

import java.util.List;
import java.util.UUID;

public record QuestlineResult(List<UUID> sectionIds, List<QuestResult> quests) { }
