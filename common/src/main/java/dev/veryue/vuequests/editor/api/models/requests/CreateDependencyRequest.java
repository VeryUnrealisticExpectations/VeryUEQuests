package dev.veryue.vuequests.editor.api.models.requests;

import java.util.UUID;

public record CreateDependencyRequest(UUID questId, UUID dependencyId, String revision) { }
