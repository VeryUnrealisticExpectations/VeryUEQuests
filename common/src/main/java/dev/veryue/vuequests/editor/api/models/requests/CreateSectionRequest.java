package dev.veryue.vuequests.editor.api.models.requests;

import net.minecraft.network.chat.Component;
import java.util.UUID;

public record CreateSectionRequest(UUID sectionId, Component name, String icon, String revision) { }
