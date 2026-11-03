package dev.veryue.vuequests.editor.api.models.responses;

import dev.veryue.vuequests.editor.api.models.RegistrySearchEntry;
import java.util.List;

public record RegistrySearchResponse(String registry, int total, int offset, int limit, List<RegistrySearchEntry> entries) { }
