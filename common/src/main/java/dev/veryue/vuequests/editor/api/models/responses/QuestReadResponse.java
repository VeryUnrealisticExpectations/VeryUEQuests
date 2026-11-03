package dev.veryue.vuequests.editor.api.models.responses;

import dev.veryue.vuequests.models.quest.Quest;

public record QuestReadResponse(Quest quest, String revision) { }
