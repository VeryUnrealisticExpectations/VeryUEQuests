package dev.veryue.vuequests.editor.api.models.requests;

import dev.veryue.vuequests.editor.api.models.ApiPosition;
import java.util.UUID;

public record ModifyQuestPositionRequest(UUID questId, ApiPosition position, String revision) { }
