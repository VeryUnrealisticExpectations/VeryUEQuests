package dev.veryue.vuequests.editor.api.models.requests;

import net.minecraft.network.chat.Component;
import java.util.UUID;

public record ModifyQuestTextRequest(UUID questId, Component title, Component subtitle,
                                     Component description, String tooltip, String revision) { }
