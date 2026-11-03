package dev.veryue.vuequests.editor.api.models.responses;

import java.util.UUID;

public record PositionResponse(UUID questId, UUID sectionId, int x, int y, String revision) { }
