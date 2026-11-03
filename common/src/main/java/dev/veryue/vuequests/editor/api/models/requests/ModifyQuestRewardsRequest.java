package dev.veryue.vuequests.editor.api.models.requests;

import dev.veryue.vuequests.editor.api.models.ListMode;
import dev.veryue.vuequests.models.reward.RewardType;
import java.util.List;
import java.util.UUID;

public record ModifyQuestRewardsRequest(UUID questId, List<RewardType<?>> rewards,
                                       ListMode mode, String revision) { }
