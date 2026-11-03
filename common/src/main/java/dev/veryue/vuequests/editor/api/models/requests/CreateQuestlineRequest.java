package dev.veryue.vuequests.editor.api.models.requests;

import java.util.List;

public record CreateQuestlineRequest(List<CreateSectionRequest> sections, List<CreateQuestRequest> quests, String revision) { }
