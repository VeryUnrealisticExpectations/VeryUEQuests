package dev.veryue.vuequests.editor.api.models.requests;

import dev.veryue.vuequests.editor.api.models.ApiPosition;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.UUID;

public record CreateQuestRequest(UUID sectionId, UUID questId, ApiPosition position, Component title,
                                 Component subtitle, Component description, String tooltip, String icon,
                                 List<QuestObjective<?, ?>> objectives, List<RewardType<?>> rewards,
                                 List<UUID> dependencies, String revision) { }
