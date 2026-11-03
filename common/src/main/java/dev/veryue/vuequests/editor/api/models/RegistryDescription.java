package dev.veryue.vuequests.editor.api.models;

import java.util.List;

public record RegistryDescription(String id, int size, List<String> usedBy) { }
