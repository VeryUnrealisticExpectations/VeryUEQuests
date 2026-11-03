package dev.veryue.vuequests.editor.api.models.responses;

import dev.veryue.vuequests.models.quest.Section;
import java.util.List;

public record SectionsResponse(List<Section> sections, String revision) { }
