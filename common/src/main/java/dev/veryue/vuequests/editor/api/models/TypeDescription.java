package dev.veryue.vuequests.editor.api.models;

import java.util.List;

public record TypeDescription(String id, List<String> fields, JsonSchema schema, boolean schemaComplete) { }
