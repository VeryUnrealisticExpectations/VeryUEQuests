package dev.veryue.vuequests.editor.api.models.responses;

import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.QuestPosition;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.reward.RewardType;
import java.util.List;
import java.util.UUID;

public record QuestResult(UUID questId, UUID sectionId, QuestPosition position,
                          List<UUID> objectiveIds, List<UUID> rewardIds) {
    public static QuestResult of(Quest quest) {
        return new QuestResult(quest.questId(), quest.sectionUUID(), quest.position(),
                quest.objectives().stream().map(QuestObjective::getObjectiveId).toList(),
                quest.rewards().stream().map(RewardType::getRewardId).toList());
    }
}
