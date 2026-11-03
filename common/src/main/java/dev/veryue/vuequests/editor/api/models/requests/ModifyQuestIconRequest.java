package dev.veryue.vuequests.editor.api.models.requests;

import java.util.UUID;

public record ModifyQuestIconRequest(UUID questId, String icon, String revision) { }
